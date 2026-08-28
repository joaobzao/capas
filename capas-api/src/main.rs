use reqwest::blocking::Client;
use scraper::{Html, Selector};
use serde::Serialize;
use std::fs::{create_dir_all, File};
use std::io::Write;
use std::thread::sleep;
use std::time::Duration;
use indexmap::IndexMap;
use unicode_normalization::UnicodeNormalization;
use base64::Engine;

// A realistic browser User-Agent. SAPO and the international sources serve
// cleanly with it; kept to avoid any bot-shaped-UA filtering.
const USER_AGENT: &str = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

#[derive(Serialize, Clone)]
struct Capa {
    id: String,
    nome: String,
    url: String,
    #[serde(rename = "lastUpdated")]
    last_updated: String,
}

// Mapeia o slug do SAPO (aria-label) para o id canónico já usado pela app,
// nos casos em que o SAPO usa um slug diferente para o mesmo jornal. Preserva
// a ordem/favoritos dos utilizadores (a app indexa por id). Jornais que o SAPO
// não tem simplesmente deixam de aparecer (sem fonte).
fn canonical_id(aria_label: &str) -> String {
    match aria_label {
        "tal-e-qual" => "tal-qual",
        "actualidad-economiaiberica" => "actualidade-economia-iberica",
        "o-caminhense" => "caminhense",
        "o-carrilhao" => "jornal-o-carrilhao",
        "o-interior" => "jornal-o-interior",
        "as" => "jornal-as",
        other => other,
    }
    .to_string()
}

// SAPO envia a data como DD/MM/YYYY; a app espera YYYY-MM-DD.
fn reformat_date(d: &str) -> String {
    let p: Vec<&str> = d.split('/').collect();
    if p.len() == 3 {
        format!("{}-{}-{}", p[2], p[1], p[0])
    } else {
        String::new()
    }
}

// O edge do SAPO rejeita pedidos de forma intermitente: ora corta a ligação
// (erro de transporte), ora devolve 404 com uma página de erro que é HTML
// perfeitamente válido. Sem verificar o status essa página era parseada como
// "secção sem capas" — falha silenciosa em vez de erro. Daí: status verificado
// e três tentativas com backoff antes de desistir.
fn get_html(client: &Client, url: &str) -> Result<String, String> {
    const TENTATIVAS: u32 = 3;
    let mut ultimo_erro = String::new();

    for tentativa in 1..=TENTATIVAS {
        match client.get(url).header("User-Agent", USER_AGENT).send() {
            Ok(resp) if resp.status().is_success() => match resp.text() {
                Ok(body) => return Ok(body),
                Err(e) => ultimo_erro = format!("falha a ler o corpo: {}", e),
            },
            Ok(resp) => ultimo_erro = format!("HTTP {}", resp.status()),
            Err(e) => ultimo_erro = e.to_string(),
        }

        if tentativa < TENTATIVAS {
            eprintln!("⚠️  {} — tentativa {}/{}: {}", url, tentativa, TENTATIVAS, ultimo_erro);
            sleep(Duration::from_millis(500 * u64::from(tentativa)));
        }
    }

    Err(ultimo_erro)
}

// Extrai as capas de uma secção do SAPO (uma única página HTML estática).
fn fetch_sapo_section(client: &Client, slug: &str) -> Result<Vec<Capa>, String> {
    let url = format!("https://sapo.pt/noticias/jornais/{}", slug);
    let body = get_html(client, &url).map_err(|e| format!("secção SAPO '{}': {}", slug, e))?;
    Ok(parse_sapo_section(&body))
}

fn parse_sapo_section(body: &str) -> Vec<Capa> {
    let doc = Html::parse_document(body);
    let trigger = Selector::parse("a.trigger").unwrap();
    // Redimensionar o thumb do SAPO para ~800px de largura (ver COVER_W/H abaixo).
    let w_re = regex::Regex::new(r"([?&])W=\d+").unwrap();
    let h_re = regex::Regex::new(r"([?&])H=\d+").unwrap();

    let mut capas = Vec::new();
    let mut seen = std::collections::HashSet::new();
    for a in doc.select(&trigger) {
        let el = a.value();
        let (Some(href), Some(label), Some(title)) =
            (el.attr("href"), el.attr("aria-label"), el.attr("data-title"))
        else {
            continue;
        };

        let id = canonical_id(label);
        // Evita duplicados (layouts responsivos podem repetir a mesma capa).
        if !seen.insert(id.clone()) {
            continue;
        }

        // A app usa o mesmo url na grelha e no detalhe. ~800px (webp) é nítido
        // no detalhe mas leve na grelha; a resolução nativa (~1600px, ~550KB)
        // tornava o download demasiado lento, sobretudo com muitas capas.
        const COVER_W: &str = "800";
        const COVER_H: &str = "1200";
        let cover_url = h_re
            .replace(
                &w_re.replace(href, format!("${{1}}W={}", COVER_W)),
                format!("${{1}}H={}", COVER_H),
            )
            .into_owned();

        let last_updated = el.attr("data-date").map(reformat_date).unwrap_or_default();

        capas.push(Capa {
            id,
            nome: title.to_string(),
            url: cover_url,
            last_updated,
        });
    }
    capas
}

// Secções portuguesas sem uma única capa. O Internacional fica de fora: vem de
// fontes de terceiros e é best-effort, não deve bloquear a publicação.
fn seccoes_vazias(resultado: &IndexMap<String, Vec<Capa>>) -> Vec<&str> {
    resultado
        .iter()
        .filter(|(secao, capas)| secao.as_str() != "Internacional" && capas.is_empty())
        .map(|(secao, _)| secao.as_str())
        .collect()
}

fn slugify(name: &str) -> String {
    name.nfkd()
        .filter(|c| c.is_ascii())
        .collect::<String>()
        .to_lowercase()
        .replace(|c: char| !c.is_alphanumeric(), "-")
        .split('-')
        .filter(|s| !s.is_empty())
        .collect::<Vec<_>>()
        .join("-")
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let client = Client::new();

    // 1-5. Capas portuguesas a partir do SAPO (sapo.pt/noticias/jornais/*).
    //   Substitui o vercapas, que passou a estar atrás da Cloudflare e bloqueia
    //   os IPs de datacenter do CI. O SAPO serve tudo em HTML estático (sem
    //   páginas de detalhe): cada capa é um <a class="trigger"> com href para a
    //   imagem (thumbs.web.sapo.io), aria-label (slug), data-title (nome),
    //   data-date (DD/MM/YYYY) e data-pswp-width/height (resolução nativa).
    let seccoes = [
        ("nacional", "Jornais Nacionais"),
        ("desporto", "Desporto"),
        ("economia", "Economia e Gestão"),
        ("local", "Regionais"),
    ];

    let mut resultado: IndexMap<String, Vec<Capa>> = IndexMap::new();
    for (slug, chave) in seccoes {
        let mut capas = fetch_sapo_section(&client, slug)?;

        // No Desporto, manter A Bola / Record / O Jogo no topo (ordenação
        // estável: os restantes preservam a ordem do SAPO).
        if chave == "Desporto" {
            let ordem = ["A Bola", "Record", "O Jogo"];
            capas.sort_by_key(|c| ordem.iter().position(|&n| n == c.nome).unwrap_or(usize::MAX));
        }

        resultado.insert(chave.to_string(), capas);
        sleep(Duration::from_millis(300));
    }

    // 5b. O SAPO guarda alguns jornais estrangeiros em resolução muito baixa.
    //     Onde existir a mesma capa em alta resolução nas fontes do Internacional,
    //     trocar. Best-effort: nunca falha o run nem esvazia uma secção.
    upgrade_capas_de_baixa_resolucao(&client, &mut resultado);

    // 6. Buscar capas internacionais (frontpages.com / giornalone.it em alta
    //    resolução, kiosko.net como fallback para os jornais não disponíveis)
    let internacional = fetch_international_covers(&client);
    if !internacional.is_empty() {
        resultado.insert("Internacional".to_string(), internacional);
    }

    // 6b. Guarda de segurança, por secção. O total somado não servia: com os
    //     Regionais cheios (~145) e os Jornais Nacionais a zero o total passava
    //     à vontade e publicávamos um capas.json com a secção principal vazia.
    //     Se o SAPO mudar o HTML e uma secção deixar de dar capas, NÃO gravamos:
    //     saímos com erro para o deploy abortar e manter o último capas.json bom.
    let vazias = seccoes_vazias(&resultado);
    if !vazias.is_empty() {
        eprintln!(
            "❌ Secções sem capas: {} — provável mudança no HTML do SAPO. A abortar sem gravar.",
            vazias.join(", ")
        );
        std::process::exit(1);
    }

    // 7. Guardar JSON
    create_dir_all("public")?;
    let mut file = File::create("public/capas.json")?;
    let json = serde_json::to_string_pretty(&resultado)?;
    file.write_all(json.as_bytes())?;

    println!("✅ Gerado: public/capas.json com sucesso!");
    Ok(())
}

// Jornais estrangeiros cujo original no SAPO é de baixa resolução: o
// thumbs.web.sapo.io nunca amplia, devolve no máximo o tamanho guardado, e para
// estes o SAPO só tem 200-750px de largura (o L'Équipe e o Tuttosport chegam a
// 200px, ilegíveis no detalhe). As mesmas capas existem a 1200px+ nas fontes
// que já usamos no Internacional. Chave: id canónico (ver canonical_id).
//
// Só entram aqui jornais verificados um a um como sendo a MESMA publicação —
// p.ex. o frontpages tem "Le Figaro" mas não o suplemento "Le Figaro Sport", e
// das quatro edições do Mundo Deportivo só a principal tem equivalente.
fn fonte_alternativa(id: &str) -> Option<Source> {
    let source = match id {
        // Desporto
        "l-equipe" => Source::Kiosko { country: "fr", paper: "l_equip" },
        "tuttosport" => Source::FrontPages { domain: GN, path: "/prima-pagina-tuttosport/" },
        "superdeporte" => Source::FrontPages { domain: FP, path: "/superdeporte/" },
        "il-romanista" => Source::FrontPages { domain: GN, path: "/prima-pagina-il-romanista/" },
        "ole-argentina" => Source::Kiosko { country: "ar", paper: "ole" },
        "gazzetta-dello-sport" => Source::Kiosko { country: "it", paper: "gazzetta_sport" },
        "mundo-deportivo" => Source::FrontPages { domain: FP, path: "/mundo-deportivo/" },
        "corriere-dello-sport" => {
            Source::FrontPages { domain: GN, path: "/prima-pagina-corriere-dello-sport/" }
        }
        // Economia e Gestão
        "la-tribune" => Source::FrontPages { domain: FP, path: "/la-tribune/" },
        // Atenção: o SAPO tem dois "El Economista" — o espanhol (este) e o
        // mexicano (id "el-economista"), que não tem fonte alternativa.
        "el-economista-spain" => Source::FrontPages { domain: FP, path: "/el-economista/" },
        "expansion" => Source::FrontPages { domain: FP, path: "/expansion/" },
        "cinco-dias" => Source::Kiosko { country: "es", paper: "5dias" },
        "valor-economico" => Source::FrontPages { domain: FP, path: "/valor-economico/" },
        _ => return None,
    };
    Some(source)
}

// Troca a capa do SAPO pela versão em alta resolução, quando existe. Best-effort:
// qualquer falha deixa ficar a do SAPO, que é de baixa qualidade mas é válida.
fn upgrade_capas_de_baixa_resolucao(client: &Client, resultado: &mut IndexMap<String, Vec<Capa>>) {
    let mut trocadas = 0;
    let mut tentadas = 0;

    for capa in resultado.values_mut().flatten() {
        let Some(source) = fonte_alternativa(&capa.id) else {
            continue;
        };
        tentadas += 1;

        let Some((url, data)) = resolve_source(client, &source) else {
            eprintln!("⚠️  {}: fonte alternativa indisponível, fica a do SAPO", capa.id);
            continue;
        };

        // Nunca trocar atualidade por nitidez: se a fonte alternativa ainda não
        // publicou a capa de hoje, a do SAPO (mais recente) é a melhor escolha.
        if !melhor_data(&data, &capa.last_updated) {
            eprintln!(
                "ℹ️  {}: alternativa é de {} e o SAPO tem {}, fica a do SAPO",
                capa.id, data, capa.last_updated
            );
            continue;
        }

        capa.url = url;
        capa.last_updated = data;
        trocadas += 1;
    }

    println!("🔍 Alta resolução: {} de {} capas substituídas", trocadas, tentadas);
}

// Datas em YYYY-MM-DD comparam-se lexicograficamente.
fn melhor_data(alternativa: &str, sapo: &str) -> bool {
    alternativa >= sapo
}

// Where a given international cover is sourced from. frontpages.com and its
// Italian sibling giornalone.it serve 1200px WebP covers behind the same
// base64-obfuscated `/g/` URL scheme; kiosko.net is the fallback for papers
// frontpages.com doesn't carry (The Daily Telegraph, Daily Mail, WSJ).
const FP: &str = "https://www.frontpages.com";
const GN: &str = "https://www.giornalone.it";

enum Source {
    // `domain` e.g. "https://www.frontpages.com", `path` e.g. "/el-pais/"
    FrontPages { domain: &'static str, path: &'static str },
    // kiosko.net `country`/`paper` slugs, no width suffix (960px original)
    Kiosko { country: &'static str, paper: &'static str },
}

fn fetch_international_covers(client: &Client) -> Vec<Capa> {
    // Display names are kept byte-for-byte identical to preserve each cover's
    // `id = slugify(nome)`, which the app persists for ordering/favorites.
    let papers = vec![
        ("El País", Source::FrontPages { domain: FP, path: "/el-pais/" }),
        ("The Daily Telegraph", Source::Kiosko { country: "uk", paper: "daily_telegraph" }),
        ("Daily Mail", Source::Kiosko { country: "uk", paper: "daily_mail" }),
        ("Financial Times", Source::FrontPages { domain: FP, path: "/financial-times/" }),
        ("Corriere della Sera", Source::FrontPages { domain: GN, path: "/prima-pagina-corriere-della-sera/" }),
        ("La Gazzetta dello Sport", Source::FrontPages { domain: FP, path: "/la-gazzetta-dello-sport/" }),
        ("The New York Times", Source::FrontPages { domain: FP, path: "/the-new-york-times/" }),
        ("Washington Post", Source::FrontPages { domain: FP, path: "/the-washington-post/" }),
        ("The Wall Street Journal", Source::Kiosko { country: "us", paper: "wsj" }),
        ("Folha de S.Paulo", Source::FrontPages { domain: FP, path: "/folha-de-s-paulo/" }),
    ];

    let mut covers = Vec::new();

    for (display_name, source) in &papers {
        let resolved = resolve_source(client, source);

        if let Some((url, last_updated)) = resolved {
            covers.push(Capa {
                id: slugify(display_name),
                nome: display_name.to_string(),
                url,
                last_updated,
            });
        }
    }

    println!("🌍 Internacional: {} de {} capas encontradas", covers.len(), papers.len());
    covers
}

fn resolve_source(client: &Client, source: &Source) -> Option<(String, String)> {
    match source {
        Source::FrontPages { domain, path } => fetch_frontpages_cover(client, domain, path),
        Source::Kiosko { country, paper } => fetch_kiosko_cover(client, country, paper),
    }
}

// Resolve a cover from a frontpages.com-family page. The full-res `/g/` image
// URL is base64-encoded in an inline `atob(...)` script; we anchor on the
// encoded payload (base64 of "/g/" is always "L2cv") so string-splitting of the
// `atob` call doesn't matter. Falls back to the un-obfuscated `@2x` thumbnail.
fn fetch_frontpages_cover(client: &Client, domain: &str, path: &str) -> Option<(String, String)> {
    let page_url = format!("{}{}", domain, path);
    sleep(Duration::from_millis(300));

    // Mesma razão do SAPO: status verificado e com tentativas. Aqui a falha não
    // é fatal — o Internacional é best-effort e só perde este jornal.
    let html = get_html(client, &page_url)
        .map_err(|e| eprintln!("⚠️  {}: {}", page_url, e))
        .ok()?;

    // Full-res /g/ path, base64-encoded.
    let b64_re = regex::Regex::new(r"L2cv[A-Za-z0-9+/=]+").unwrap();
    if let Some(m) = b64_re.find(&html) {
        if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(m.as_str()) {
            if let Ok(decoded_path) = String::from_utf8(bytes) {
                if decoded_path.starts_with("/g/") {
                    let url = format!("{}{}", domain, decoded_path);
                    if image_exists(client, &url) {
                        let last_updated = date_from_slash_path(&decoded_path);
                        return Some((url, last_updated));
                    }
                }
            }
        }
    }

    // Fallback: the 600x800 retina thumbnail is present un-obfuscated in the HTML.
    let thumb_re =
        regex::Regex::new(r"/t/\d{4}/\d{2}/\d{2}/[a-z0-9-]+@2x\.webp").unwrap();
    if let Some(m) = thumb_re.find(&html) {
        let thumb_path = m.as_str();
        let url = format!("{}{}", domain, thumb_path);
        if image_exists(client, &url) {
            let last_updated = date_from_slash_path(thumb_path);
            return Some((url, last_updated));
        }
    }

    None
}

// Resolve a cover from kiosko.net's no-suffix original (960px), trying today
// then yesterday.
fn fetch_kiosko_cover(client: &Client, country: &str, paper: &str) -> Option<(String, String)> {
    let today = chrono::Local::now().date_naive();
    let yesterday = today - chrono::Duration::days(1);

    for date in &[today, yesterday] {
        let url = format!(
            "https://img.kiosko.net/{}/{}/{}.jpg",
            date.format("%Y/%m/%d"),
            country,
            paper
        );

        sleep(Duration::from_millis(300));

        if image_exists(client, &url) {
            return Some((url, date.format("%Y-%m-%d").to_string()));
        }
    }

    None
}

// Cheap existence check: a single-byte ranged GET, accepting 200 or 206.
fn image_exists(client: &Client, url: &str) -> bool {
    client
        .get(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        .header("Range", "bytes=0-0")
        .send()
        .map(|resp| {
            let status = resp.status();
            status.is_success() || status.as_u16() == 206
        })
        .unwrap_or(false)
}

// Extract YYYY-MM-DD from a `/g/YYYY/MM/DD/...` or `/t/YYYY/MM/DD/...` path.
fn date_from_slash_path(path: &str) -> String {
    regex::Regex::new(r"/(\d{4})/(\d{2})/(\d{2})/")
        .unwrap()
        .captures(path)
        .map(|c| format!("{}-{}-{}", &c[1], &c[2], &c[3]))
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn capa_exemplo() -> Capa {
        Capa {
            id: "publico".to_string(),
            nome: "Público".to_string(),
            url: "https://thumbs.web.sapo.io/?W=800".to_string(),
            last_updated: "2026-08-24".to_string(),
        }
    }

    // Uma âncora igual às que o SAPO serve, com o thumb em resolução nativa.
    fn ancora(label: &str, title: &str) -> String {
        format!(
            r#"<a class="trigger" href="https://thumbs.web.sapo.io/?W=1600&H=2400&epic=abc"
               aria-label="{label}" data-title="{title}" data-date="24/08/2026"></a>"#
        )
    }

    #[test]
    fn parse_extrai_capa_e_redimensiona_para_800px() {
        let capas = parse_sapo_section(&ancora("publico", "Público"));

        assert_eq!(capas.len(), 1);
        assert_eq!(capas[0].id, "publico");
        assert_eq!(capas[0].nome, "Público");
        assert_eq!(capas[0].last_updated, "2026-08-24");
        assert_eq!(capas[0].url, "https://thumbs.web.sapo.io/?W=800&H=1200&epic=abc");
    }

    // Quando o SAPO rejeita o pedido devolve uma página de erro: HTML válido,
    // sem um único <a class="trigger">. É indistinguível de "secção sem capas",
    // e é por isso que a guarda por secção existe.
    #[test]
    fn pagina_de_rejeicao_do_sapo_nao_produz_capas() {
        let capas = parse_sapo_section("<html><body><h1>SAPO</h1></body></html>");

        assert!(capas.is_empty());
    }

    // Servidor de teste: responde `respostas` em sequência, uma por ligação.
    fn servidor(respostas: Vec<&'static str>) -> String {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let addr = listener.local_addr().unwrap();
        std::thread::spawn(move || {
            for (stream, resposta) in listener.incoming().zip(respostas) {
                let mut stream = stream.unwrap();
                let mut buf = [0u8; 1024];
                let _ = std::io::Read::read(&mut stream, &mut buf);
                let _ = stream.write_all(resposta.as_bytes());
            }
        });
        format!("http://{}/", addr)
    }

    // Uma falha transitória do SAPO (ligação cortada, 5xx) chegou a esvaziar
    // uma secção inteira num run real. Tem de voltar a tentar.
    #[test]
    fn get_html_volta_a_tentar_apos_falha_transitoria() {
        let url = servidor(vec![
            "HTTP/1.1 503 Service Unavailable\r\nContent-Length: 0\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok",
        ]);

        assert_eq!(get_html(&Client::new(), &url), Ok("ok".to_string()));
    }

    #[test]
    fn guarda_deteta_seccao_portuguesa_vazia() {
        let mut resultado: IndexMap<String, Vec<Capa>> = IndexMap::new();
        resultado.insert("Jornais Nacionais".to_string(), Vec::new());
        resultado.insert("Desporto".to_string(), vec![capa_exemplo()]);

        assert_eq!(seccoes_vazias(&resultado), vec!["Jornais Nacionais"]);
    }

    // O Internacional é best-effort (fontes de terceiros, sem contrato); ficar
    // vazio não deve impedir a publicação das capas portuguesas.
    #[test]
    fn guarda_ignora_internacional_vazio() {
        let mut resultado: IndexMap<String, Vec<Capa>> = IndexMap::new();
        resultado.insert("Jornais Nacionais".to_string(), vec![capa_exemplo()]);
        resultado.insert("Internacional".to_string(), Vec::new());

        assert!(seccoes_vazias(&resultado).is_empty());
    }

    // O SAPO guarda estes dois a 200px de largura; sem fonte alternativa a capa
    // fica ilegível no detalhe.
    #[test]
    fn jornais_de_baixa_resolucao_tem_fonte_alternativa() {
        for id in ["l-equipe", "tuttosport", "superdeporte", "mundo-deportivo"] {
            assert!(fonte_alternativa(id).is_some(), "{} sem fonte alternativa", id);
        }
    }

    // Só a edição principal do Mundo Deportivo tem equivalente no frontpages; as
    // regionais são outras capas e não podem ser substituídas pela principal.
    #[test]
    fn edicoes_sem_equivalente_ficam_no_sapo() {
        for id in [
            "mundo-deportivo-gipuzkoa",
            "mundo-deportivo-atletico",
            "le-figaro-sport",
            "el-economista",
            "publico",
        ] {
            assert!(fonte_alternativa(id).is_none(), "{} não devia ser substituído", id);
        }
    }

    // Uma capa mais nítida mas de ontem é pior do que a de hoje: a fonte
    // alternativa só ganha se estiver tão atualizada como o SAPO.
    #[test]
    fn alternativa_desatualizada_nao_substitui_o_sapo() {
        assert!(melhor_data("2026-08-28", "2026-08-28"));
        assert!(melhor_data("2026-08-29", "2026-08-28"));
        assert!(!melhor_data("2026-08-27", "2026-08-28"));
    }
}

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

// Extrai as capas de uma secção do SAPO (uma única página HTML estática).
fn fetch_sapo_section(client: &Client, slug: &str) -> Vec<Capa> {
    let url = format!("https://sapo.pt/noticias/jornais/{}", slug);
    let Ok(resp) = client.get(&url).header("User-Agent", USER_AGENT).send() else {
        eprintln!("⚠️  Falha a obter secção SAPO '{}'", slug);
        return Vec::new();
    };
    let Ok(body) = resp.text() else {
        eprintln!("⚠️  Falha a ler secção SAPO '{}'", slug);
        return Vec::new();
    };

    let doc = Html::parse_document(&body);
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
        let mut capas = fetch_sapo_section(&client, slug);

        // No Desporto, manter A Bola / Record / O Jogo no topo (ordenação
        // estável: os restantes preservam a ordem do SAPO).
        if chave == "Desporto" {
            let ordem = ["A Bola", "Record", "O Jogo"];
            capas.sort_by_key(|c| ordem.iter().position(|&n| n == c.nome).unwrap_or(usize::MAX));
        }

        resultado.insert(chave.to_string(), capas);
        sleep(Duration::from_millis(300));
    }

    // 6. Buscar capas internacionais (frontpages.com / giornalone.it em alta
    //    resolução, kiosko.net como fallback para os jornais não disponíveis)
    let internacional = fetch_international_covers(&client);
    if !internacional.is_empty() {
        resultado.insert("Internacional".to_string(), internacional);
    }

    // 6b. Guarda de segurança: se a fonte das capas portuguesas (SAPO) falhar
    //     ou bloquear, as secções nacionais vêm vazias e só sobra o Internacional.
    //     Nesse caso NÃO gravamos: saímos com erro para o publish script abortar
    //     e manter o último capas.json válido em vez de publicar lixo.
    let nacionais_total: usize = resultado
        .iter()
        .filter(|(secao, _)| secao.as_str() != "Internacional")
        .map(|(_, capas)| capas.len())
        .sum();
    if nacionais_total < 5 {
        eprintln!(
            "❌ Apenas {} capas nacionais encontradas — provável falha do SAPO. A abortar sem gravar.",
            nacionais_total
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

// Where a given international cover is sourced from. frontpages.com and its
// Italian sibling giornalone.it serve 1200px WebP covers behind the same
// base64-obfuscated `/g/` URL scheme; kiosko.net is the fallback for papers
// frontpages.com doesn't carry (The Daily Telegraph, Daily Mail, WSJ).
enum Source {
    // `domain` e.g. "https://www.frontpages.com", `path` e.g. "/el-pais/"
    FrontPages { domain: &'static str, path: &'static str },
    // kiosko.net `country`/`paper` slugs, no width suffix (960px original)
    Kiosko { country: &'static str, paper: &'static str },
}

fn fetch_international_covers(client: &Client) -> Vec<Capa> {
    const FP: &str = "https://www.frontpages.com";
    const GN: &str = "https://www.giornalone.it";

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
        let resolved = match source {
            Source::FrontPages { domain, path } => fetch_frontpages_cover(client, domain, path),
            Source::Kiosko { country, paper } => fetch_kiosko_cover(client, country, paper),
        };

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

// Resolve a cover from a frontpages.com-family page. The full-res `/g/` image
// URL is base64-encoded in an inline `atob(...)` script; we anchor on the
// encoded payload (base64 of "/g/" is always "L2cv") so string-splitting of the
// `atob` call doesn't matter. Falls back to the un-obfuscated `@2x` thumbnail.
fn fetch_frontpages_cover(client: &Client, domain: &str, path: &str) -> Option<(String, String)> {
    let page_url = format!("{}{}", domain, path);
    sleep(Duration::from_millis(300));

    let html = client
        .get(&page_url)
        .header("User-Agent", USER_AGENT)
        .send()
        .ok()?
        .text()
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

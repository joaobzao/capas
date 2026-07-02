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

// vercapas filters requests by User-Agent; bot-shaped UAs get blocked after
// ~20 requests with TCP resets. A realistic browser UA passes cleanly.
const USER_AGENT: &str = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

#[derive(Serialize, Clone)]
struct Capa {
    id: String,
    nome: String,
    url: String,
    #[serde(rename = "lastUpdated")]
    last_updated: String,
}

fn extract_date_from_url(url: &str) -> String {
    let re = regex::Regex::new(r"(\d{4}-\d{2}-\d{2})").unwrap();
    re.find(url)
        .map(|m| m.as_str().to_string())
        .unwrap_or_default()
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
    let base = "https://www.vercapas.com";
    let client = Client::new();

    // 1. Página principal
    let body = client
        .get(base)
        .header("User-Agent", USER_AGENT)
        .send()?
        .text()?;
    let document = Html::parse_document(&body);

    // Seletores
    let section_selector = Selector::parse("section").unwrap();
    let title_selector = Selector::parse("h2").unwrap();
    let link_selector = Selector::parse("a").unwrap();
    let img_selector = Selector::parse("img").unwrap();

    let mut resultado_temp: IndexMap<String, Vec<Capa>> = IndexMap::new();

    // Adicionado "Jornais Regionais" caso o site mude o título ligeiramente
    let secoes_permitidas = vec![
        "Jornais Nacionais", 
        "Desporto", 
        "Economia e Gestão", 
        "Regionais", 
        "Jornais Regionais"
    ];
    let mover_para_desporto = ["O Jogo", "A Bola", "Record", "Jornal Record"];

    // 2. Iterar pelas secções da homepage
    for section in document.select(&section_selector) {
        let secao = section
            .select(&title_selector)
            .next()
            .map(|el| el.text().collect::<String>().trim().to_string())
            .unwrap_or_else(|| "Outros".to_string());

        if !secoes_permitidas.contains(&secao.as_str()) {
            continue;
        }

        let mut capas_secao = Vec::new();

        for link in section.select(&link_selector) {
            let Some(href) = link.value().attr("href") else { continue };
            if !href.contains("/capa/") && !href.contains("/covers/") {
                continue;
            }
            let capa_url = if href.starts_with("http") {
                href.to_string()
            } else {
                format!("{}{}", base, href)
            };

            let mut nome = String::from("desconhecido");
            for img in link.select(&img_selector) {
                if let Some(alt) = img.value().attr("alt") {
                    nome = alt.to_string();
                }
            }

            sleep(Duration::from_millis(200));

            // The detail page is the only place the full-size cover URL lives —
            // the homepage only carries the thumbnail (different hash for some papers).
            let Ok(resp) = client.get(&capa_url).header("User-Agent", USER_AGENT).send() else { continue };
            let Ok(text) = resp.text() else { continue };
            let capa_doc = Html::parse_document(&text);
            let big_img_selector = Selector::parse("img").unwrap();

            for img in capa_doc.select(&big_img_selector) {
                let Some(src) = img.value().attr("src") else { continue };
                if !src.contains("covers") {
                    continue;
                }
                let url = if src.starts_with("http") {
                    src.to_string()
                } else {
                    format!("{}{}", base, src)
                };
                let last_updated = extract_date_from_url(&url);
                let id_name = match nome.as_str() {
                    "Jornal Record" => "Record",
                    other => other,
                };
                capas_secao.push(Capa {
                    id: slugify(id_name),
                    nome: nome.clone(),
                    url,
                    last_updated,
                });
                break;
            }
        }

        if !capas_secao.is_empty() {
            // Normaliza a chave para garantir que encontramos depois
            let chave = if secao.contains("Regionais") { "Regionais".to_string() } else { secao };
            resultado_temp.insert(chave, capas_secao);
        }
    }

    // 3. Mover jornais de Nacionais → Desporto
    let nacionais = resultado_temp
        .shift_remove("Jornais Nacionais")
        .unwrap_or_default();
    let desporto = resultado_temp.shift_remove("Desporto").unwrap_or_default();

    let mut restantes = Vec::new();
    let mut desporto_full = desporto;

    for capa in nacionais {
        if mover_para_desporto.contains(&capa.nome.as_str()) {
            desporto_full.push(capa);
        } else {
            restantes.push(capa);
        }
    }

    // 4. Ordenar Desporto
    let ordem_preferida = ["A Bola", "Record", "O Jogo"];
    let mut prioridade = Vec::new();
    let mut resto = Vec::new();

    for capa in desporto_full {
        if ordem_preferida.contains(&capa.nome.as_str()) {
            prioridade.push(capa);
        } else {
            resto.push(capa);
        }
    }

    prioridade.sort_by_key(|c| {
        ordem_preferida
            .iter()
            .position(|&x| x == c.nome)
            .unwrap_or(usize::MAX)
    });

    let mut final_desporto = prioridade;
    final_desporto.extend(resto);

    // 5. Construir resultado final em ordem fixa
    let mut resultado: IndexMap<String, Vec<Capa>> = IndexMap::new();
    
    resultado.insert("Jornais Nacionais".to_string(), restantes);
    resultado.insert("Desporto".to_string(), final_desporto);

    if let Some(economia) = resultado_temp.shift_remove("Economia e Gestão") {
        resultado.insert("Economia e Gestão".to_string(), economia);
    }

    if let Some(mut regionais) = resultado_temp.shift_remove("Regionais") {
        // Opcional: Ordenar regionais alfabeticamente pois costumam ser muitos
        regionais.sort_by(|a, b| a.nome.cmp(&b.nome)); 
        resultado.insert("Regionais".to_string(), regionais);
    }

    // 6. Buscar capas internacionais (frontpages.com / giornalone.it em alta
    //    resolução, kiosko.net como fallback para os jornais não disponíveis)
    let internacional = fetch_international_covers(&client);
    if !internacional.is_empty() {
        resultado.insert("Internacional".to_string(), internacional);
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

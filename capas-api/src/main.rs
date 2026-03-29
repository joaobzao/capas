use reqwest::blocking::Client;
use scraper::{Html, Selector};
use serde::Serialize;
use std::fs::{create_dir_all, File};
use std::io::Write;
use std::thread::sleep;
use std::time::Duration;
use indexmap::IndexMap; 
use unicode_normalization::UnicodeNormalization;

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
        .header("User-Agent", "Mozilla/5.0 (CapasBot/1.0)")
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
    let mover_para_desporto = ["O Jogo", "A Bola", "Record"];

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
            if let Some(href) = link.value().attr("href") {
                if href.contains("/capa/") || href.contains("/covers/") {
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

                    // 💤 pequena pausa
                    sleep(Duration::from_millis(200)); 

                    let capa_body = client
                        .get(&capa_url)
                        .header("User-Agent", "Mozilla/5.0 (CapasBot/1.0)")
                        .send();

                    // Tratamento simples de erro na requisição individual
                    if let Ok(resp) = capa_body {
                        if let Ok(text) = resp.text() {
                            let capa_doc = Html::parse_document(&text);
                            let big_img_selector = Selector::parse("img").unwrap();
                            
                            for img in capa_doc.select(&big_img_selector) {
                                if let Some(src) = img.value().attr("src") {
                                    if src.contains("covers") {
                                        let url = if src.starts_with("http") {
                                            src.to_string()
                                        } else {
                                            format!("{}{}", base, src)
                                        };

                                        let last_updated = extract_date_from_url(&url);
                                        capas_secao.push(Capa {
                                            id: slugify(&nome),
                                            nome: nome.clone(),
                                            url,
                                            last_updated,
                                        });
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
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

    // 6. Buscar capas internacionais do kiosko.net
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

fn fetch_international_covers(client: &Client) -> Vec<Capa> {
    let papers = vec![
        ("es", "elpais", "El País"),
        ("uk", "the_times", "The Times"),
        ("uk", "guardian", "The Guardian"),
        ("uk", "ft_uk", "Financial Times"),
        ("it", "corriere_della_sera", "Corriere della Sera"),
        ("it", "gazzetta_sport", "La Gazzetta dello Sport"),
        ("us", "newyork_times", "The New York Times"),
        ("us", "washington_post", "Washington Post"),
        ("us", "wsj", "The Wall Street Journal"),
        ("br", "br_folha_spaulo", "Folha de S.Paulo"),
    ];

    let today = chrono::Local::now().date_naive();
    let yesterday = today - chrono::Duration::days(1);

    let mut covers = Vec::new();

    for (country, paper_id, display_name) in &papers {
        // Try today first, then yesterday as fallback
        for date in &[today, yesterday] {
            let url = format!(
                "https://img.kiosko.net/{}/{}/{}.750.jpg",
                date.format("%Y/%m/%d"),
                country,
                paper_id
            );

            sleep(Duration::from_millis(300));

            if let Ok(resp) = client
                .get(&url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Range", "bytes=0-0")
                .send()
            {
                let status = resp.status();
                if status.is_success() || status.as_u16() == 206 {
                    covers.push(Capa {
                        id: slugify(display_name),
                        nome: display_name.to_string(),
                        url,
                        last_updated: date.format("%Y-%m-%d").to_string(),
                    });
                    break;
                }
            }
        }
    }

    println!("🌍 Internacional: {} de {} capas encontradas", covers.len(), papers.len());
    covers
}

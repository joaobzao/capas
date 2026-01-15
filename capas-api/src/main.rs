use indexmap::IndexMap;
use reqwest::Client;
use scraper::{Html, Selector};
use serde::{Deserialize, Serialize};
use std::fs::{create_dir_all, File};
use std::io::Write;
use std::time::Duration;
use tokio::time::sleep;
use unicode_normalization::UnicodeNormalization;

mod gemini;
use gemini::GeminiClient;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct NewsItem {
    pub headline: String,
    pub summary: Option<String>,
    pub category: Option<String>,
}

#[derive(Serialize, Deserialize, Clone)]
struct Capa {
    id: String,
    nome: String,
    url: String,
    news: Option<Vec<NewsItem>>,
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

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let base = "https://www.vercapas.com";
    let client = Client::new();
    let gemini = GeminiClient::new().ok(); // Optional Gemini client

    if gemini.is_none() {
        println!("⚠️ GEMINI_API_KEY not set. News catalog generation will be skipped.");
    } else {
        println!("✨ Gemini Client initialized. News catalog will be generated.");
    }

    // 1. Página principal
    let body = client
        .get(base)
        .header("User-Agent", "Mozilla/5.0 (CapasBot/1.0)")
        .send()
        .await?
        .text()
        .await?;
    let document = Html::parse_document(&body);

    // Seletores
    let section_selector = Selector::parse("section").unwrap();
    let title_selector = Selector::parse("h2").unwrap();
    let link_selector = Selector::parse("a").unwrap();
    let img_selector = Selector::parse("img").unwrap();

    let mut resultado_temp: IndexMap<String, Vec<Capa>> = IndexMap::new();

    let secoes_permitidas = vec![
        "Jornais Nacionais",
        "Desporto",
        "Economia e Gestão",
        "Regionais",
        "Jornais Regionais",
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

        // Collect links to visit for this section
        let mut links_to_visit = Vec::new();
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
                    links_to_visit.push((capa_url, nome));
                }
            }
        }

        // Process in batches (e.g., 5 at a time) to respect free tier and optimize
        for batch in links_to_visit.chunks(5) {
             // 1. Resolve actual image URLs for the batch
             let mut cover_images = Vec::new();
             
             for (capa_url, nome) in batch {
                 let resp = client
                    .get(capa_url)
                    .header("User-Agent", "Mozilla/5.0 (CapasBot/1.0)")
                    .send()
                    .await;
                
                 if let Ok(resp) = resp {
                    if let Ok(text) = resp.text().await {
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
                                    println!("Found cover: {} ({})", nome, url);
                                    cover_images.push((url, nome.clone()));
                                    break;
                                }
                            }
                        }
                    }
                 }
             }

             // 2. Batch Analyze with AI
             let mut batch_results = std::collections::HashMap::new();
             
             // ENABLE AI ONLY FOR SPECIFIC SECTIONS
             let ai_sections = ["Jornais Nacionais", "Desporto"];
             let should_analyze = ai_sections.contains(&secao.as_str());

             if !cover_images.is_empty() && should_analyze {
                 if let Some(g_client) = &gemini {
                    println!("   ⏳ Waiting 4s (Free Tier Rate Limit)...");
                    sleep(Duration::from_secs(4)).await;

                    println!("   Analyzing batch of {} covers...", cover_images.len());
                    match g_client.analyze_covers_batch(&cover_images).await {
                         Ok(json_data) => {
                             if let Ok(map) = serde_json::from_value::<std::collections::HashMap<String, Vec<NewsItem>>>(json_data) {
                                 batch_results = map;
                                 println!("   ✅ Batch analysis complete.");
                             } else {
                                 println!("   ⚠️ Failed to parse AI batch response.");
                             }
                         },
                         Err(e) => println!("   ❌ AI Batch Analysis failed: {}", e),
                    }
                 }
             }

             // 3. Create Capa objects
             for (url, nome) in cover_images {
                 let news = batch_results.remove(&nome);
                 capas_secao.push(Capa {
                    id: slugify(&nome),
                    nome,
                    url,
                    news,
                 });
             }
        }

        if !capas_secao.is_empty() {
            let chave = if secao.contains("Regionais") {
                "Regionais".to_string()
            } else {
                secao
            };
            resultado_temp.insert(chave, capas_secao);
        }
    }

    // 3. Mover jornais de Nacionais → Desporto
    let nacionais = resultado_temp
        .shift_remove("Jornais Nacionais")
        .unwrap_or_default();
    let desporto = resultado_temp
        .shift_remove("Desporto")
        .unwrap_or_default();

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
        regionais.sort_by(|a, b| a.nome.cmp(&b.nome));
        resultado.insert("Regionais".to_string(), regionais);
    }

    // 6. Generate Filters (Tags) if Gemini is available
    if let Some(g_client) = &gemini {
        // Aggregate all news
        let mut all_news = Vec::new();
        for capas in resultado.values() {
            for capa in capas {
                if let Some(news_items) = &capa.news {
                   all_news.extend(news_items.clone());
                }
            }
        }
        
        if !all_news.is_empty() {
             println!("🔍 Generating Smart Filters from {} news items...", all_news.len());
             match g_client.generate_filters(&all_news).await {
                 Ok(filters) => {
                     println!("✅ Filters generated: {:?}", filters);
                     let mut filter_file = File::create("public/filters.json")?;
                     let json = serde_json::to_string_pretty(&filters)?;
                     filter_file.write_all(json.as_bytes())?;
                 },
                 Err(e) => println!("❌ Failed to generate filters: {}", e),
             }
        }
    }

    // 7. Guardar capas.json
    create_dir_all("public")?;
    let mut file = File::create("public/capas.json")?;
    let json = serde_json::to_string_pretty(&resultado)?;
    file.write_all(json.as_bytes())?;

    println!("✅ Gerado: public/capas.json com sucesso!");
    Ok(())
}

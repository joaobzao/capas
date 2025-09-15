use reqwest::blocking::Client;
use scraper::{Html, Selector};
use serde::Serialize;
use std::fs::{create_dir_all, File};
use std::io::Write;

#[derive(Serialize)]
struct Capa {
    nome: String,
    url: String,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let url = "https://www.vercapas.com/";
    let client = Client::new();
    let body = client
        .get(url)
        .header("User-Agent", "Mozilla/5.0 (CapasBot/1.0)")
        .send()?
        .text()?;

    // Gravar HTML para inspecionar se precisares
    std::fs::write("debug.html", &body)?;

    let document = Html::parse_document(&body);
    let selector = Selector::parse("img").unwrap();

    let mut capas = Vec::new();

    for element in document.select(&selector) {
        if let Some(src) = element.value().attr("src") {
            if src.contains("covers") && (src.ends_with(".jpg") || src.ends_with(".jpeg") || src.ends_with(".png")) {
                let nome = element
                    .value()
                    .attr("alt")
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| {
                        // fallback: extrair do path do URL
                        src.split('/')
                            .nth_back(1) // penúltima parte é o nome do jornal
                            .unwrap_or("desconhecido")
                            .to_string()
                    });

                capas.push(Capa {
                    nome,
                    url: src.to_string(),
                });
            }
        }
    }

    create_dir_all("public")?;
    let mut file = File::create("public/capas.json")?;
    let json = serde_json::to_string_pretty(&capas)?;
    file.write_all(json.as_bytes())?;

    println!("✅ Gerado: public/capas.json ({} capas)", capas.len());
    Ok(())
}

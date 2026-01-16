use reqwest::Client;
use serde_json::json;
use std::env;
use std::error::Error;

pub struct GeminiClient {
    api_key: String,
    client: Client,
}

impl GeminiClient {
    pub fn new() -> Result<Self, Box<dyn Error>> {
        let api_key = env::var("GEMINI_API_KEY").map_err(|_| "GEMINI_API_KEY must be set")?;
        Ok(Self {
            api_key,
            client: Client::new(),
        })
    }

    pub async fn generate_digest(&self, covers: &[(String, String)]) -> Result<serde_json::Value, Box<dyn Error>> {
        let mut parts = Vec::new();
        
        // 1. Prepare Prompt
        let mut prompt_text = String::from(
            "Act as a professional news editor. Analyze these newspaper covers (from Portugal) and create a 'Daily News Digest'.\n\
             Your goal is to identify the most important and common stories across these covers.\n\n\
             Instructions:\n\
             1. Aggregate the main stories. If multiple newspapers talk about the same topic, combine them into one entry.\n\
             2. Separate stories into two main categories based on the source and content: 'Nacional' (General News) and 'Desporto' (Sports).\n\
             3. For each story, provide:\n\
             - 'title': A concise, engaging headline in Portuguese.\n\
             - 'summary': A brief summary of the event (max 2 sentences) in Portuguese.\n\
             - 'sources': A list of the Newspaper Names that feature this story.\n\
             - 'category': Either 'Nacional' or 'Desporto'.\n\n\
             The input images correspond to the following newspapers:\n"
        );

        for (i, (_, name)) in covers.iter().enumerate() {
            prompt_text.push_str(&format!("{}. {}\n", i + 1, name));
        }

        prompt_text.push_str("\nReturn ONLY a JSON object with this structure: { \"digest\": [ { \"title\": \"...\", \"summary\": \"...\", \"sources\": [\"...\"], \"category\": \"...\" } ] }");

        parts.push(json!({ "text": prompt_text }));

        // 2. Download and Add Images
        for (url, _) in covers {
            // Download image
            let image_data = self.client.get(url).send().await?.bytes().await?;
            let base64_image = base64::encode(&image_data);
            
            parts.push(json!({
                "inline_data": {
                    "mime_type": "image/jpeg", 
                    "data": base64_image
                }
            }));
        }

        let request_body = json!({
            "contents": [{
                "parts": parts
            }]
        });

        let url = format!(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key={}",
            self.api_key
        );

        let response = self.client.post(&url)
            .json(&request_body)
            .send()
            .await?;
            
        if !response.status().is_success() {
             let error_text = response.text().await?;
             return Err(format!("Gemini API Error: {}", error_text).into());
        }

        let response_json: serde_json::Value = response.json().await?;
        
        // 3. Parse Response
         if let Some(candidates) = response_json.get("candidates").and_then(|c| c.as_array()) {
            if let Some(first) = candidates.first() {
                if let Some(parts) = first.get("content").and_then(|c| c.get("parts")).and_then(|p| p.as_array()) {
                     if let Some(text_part) = parts.first() {
                         if let Some(text) = text_part.get("text").and_then(|t| t.as_str()) {
                             let clean_text = text.replace("```json", "").replace("```", "").trim().to_string();
                             let parsed: serde_json::Value = serde_json::from_str(&clean_text)?;
                             return Ok(parsed);
                         }
                     }
                }
            }
        }

        Ok(json!({ "digest": [] }))
    }
}

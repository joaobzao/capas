# 📰 Capas API

> API simples e gratuita que disponibiliza diariamente as capas dos principais jornais portugueses em formato JSON.  
> Construída com Rust + GitHub Actions + GitHub Pages (ou outro hosting estático).

---

## ⚙️ Como funciona

Este projeto usa um **script em Rust** para:
1. Fazer scraping à página [https://www.sapo.pt/jornais](https://www.sapo.pt/jornais)
2. Extrair URLs de imagens de capas dos jornais
3. Derivar o nome do jornal a partir do URL
4. Gerar um ficheiro `capas.json` no diretório `public/`

Uma **GitHub Action** corre diariamente (ou manualmente) para atualizar automaticamente o `capas.json`.

---

## 📦 Exemplo de resposta

```json
[
  {
    "nome": "publico",
    "url": "https://cdn.sapoimages.pt/jornais/publico/20250913.jpg"
  },
  {
    "nome": "jn",
    "url": "https://cdn.sapoimages.pt/jornais/jn/20250913.jpg"
  }
]
```

---

## 🚀 URL para consumir o JSON

Se usares **GitHub Pages**:

```
https://<teu-username>.github.io/capas-api/capas.json
```

Por exemplo:

```
https://joaobzao.github.io/capas-api/capas.json
```

---

## 🛠️ Como correr localmente

1. Instalar [Rust](https://www.rust-lang.org/tools/install)
2. Clonar o repositório:
   ```bash
   git clone https://github.com/<teu-username>/capas-api.git
   cd capas-api
   ```
3. Instalar dependências e correr:
   ```bash
   cargo run
   ```

O ficheiro `public/capas.json` será criado automaticamente.

---

## 🔁 Automatização com GitHub Actions

O ficheiro `.github/workflows/update-capas.yml` está configurado para:
- Correr automaticamente todos os dias às 07:00 UTC
- Ou manualmente via GitHub Actions → "Run workflow"
- Fazer `commit` e `push` do `capas.json` atualizado

---

## 🧱 Estrutura do projeto

```
capas-api/
├── .github/workflows/update-capas.yml  # GitHub Action
├── public/capas.json                   # JSON gerado com capas
├── src/main.rs                         # Script em Rust (scraper)
├── Cargo.toml
└── README.md
```

---

## 🧪 TODOs & melhorias futuras

- [ ] Mapeamento mais amigável dos nomes dos jornais (ex: "cm" → "Correio da Manhã")
- [ ] Adicionar timestamp ao JSON (ex: `"atualizado_em": "2025-09-13T07:00Z"`)
- [ ] Adicionar fallback/localização em caso de falha
- [ ] Deploy automático para Vercel/Netlify (opcional)

---

## 📜 Licença

Este projeto está disponível sob a licença [MIT](LICENSE).

---

Feito com ❤️ por [João Zão](https://github.com/joaobzao)

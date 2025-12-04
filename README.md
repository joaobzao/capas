# 📰 Capas

> Projeto para disponibilizar e visualizar as capas dos principais jornais portugueses diariamente.

Este repositório contém dois componentes principais:
1. **API (`capas-api`)**: Um scraper em Rust que gera um JSON com as capas.
2. **App (`capas-app`)**: Uma aplicação móvel (Android & iOS) construída com Kotlin Multiplatform para visualizar as capas.

---

## 🧱 Estrutura do Repositório

```
capas/
├── capas-api/          # Scraper e API (Rust)
├── capas-app/          # Aplicação Mobile (Kotlin Multiplatform)
└── README.md
```

---

## 🦀 Capas API

> API simples e gratuita que disponibiliza diariamente as capas dos principais jornais portugueses em formato JSON.  
> Construída com Rust + GitHub Actions + GitHub Pages.

### ⚙️ Como funciona

Este componente usa um **script em Rust** para:
1. Fazer scraping à página [https://www.vercapas.com](https://www.vercapas.com)
2. Extrair URLs de imagens de capas dos jornais
3. Derivar o nome do jornal a partir do URL
4. Gerar um ficheiro `capas.json` no diretório `public/`

Uma **GitHub Action** corre diariamente para atualizar automaticamente o `capas.json`.

### 📦 Exemplo de resposta

```json
[
  {
    "nome": "publico",
    "url": "https://www.vercapas.com/covers/20250913/publico.jpg"
  },
  {
    "nome": "jn",
    "url": "https://www.vercapas.com/covers/20250913/jn.jpg"
  }
]
```

### 🚀 URL para consumir o JSON

Se usares **GitHub Pages**:

```
https://<teu-username>.github.io/capas-api/capas.json
```

### 🛠️ Como correr a API localmente

1. Instalar [Rust](https://www.rust-lang.org/tools/install)
2. Entrar na pasta:
   ```bash
   cd capas-api
   ```
3. Instalar dependências e correr:
   ```bash
   cargo run
   ```

---

## 📱 Capas App

> Aplicação móvel para Android e iOS que consome a API e mostra as capas do dia.  
> Construída com **Kotlin Multiplatform (KMP)** e **Compose Multiplatform**.

### 🛠️ Tech Stack

- **Linguagem**: Kotlin
- **UI**: Jetpack Compose (Android) & Compose Multiplatform (iOS)
- **Networking**: Ktor
- **Injeção de Dependências**: Koin
- **Serialização**: Kotlinx Serialization
- **Carregamento de Imagens**: Coil
- **Logging**: Kermit

### 🚀 Como correr a App

#### Android
1. Abrir o projeto `capas-app` no Android Studio.
2. Selecionar a configuração `composeApp` e correr num emulador ou dispositivo.
3. Ou via terminal:
   ```bash
   cd capas-app
   ./gradlew :composeApp:assembleDebug
   ```

#### iOS
1. Abrir o projeto `capas-app` no Android Studio (com plugin KMP) ou Fleet.
2. Correr a configuração de iOS.
3. Ou abrir `capas-app/iosApp/iosApp.xcodeproj` no Xcode e correr.

---

## 📜 Licença

Este projeto está disponível sob a licença [MIT](LICENSE).

---

Feito com ❤️ por [João Zão](https://github.com/joaobzao)

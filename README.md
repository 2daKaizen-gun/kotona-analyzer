# 🇯🇵 KOTONA (Context & Tone Analyzer)
> **"단어(言葉) 이상의 맥락(Context)을 읽다"** > AI 기반 일본 비즈니스 커뮤니케이션 뉘앙스 분석 및 대응 지원 솔루션
---
## 🚀 프로젝트 개요 (Overview)
일본 비즈니스 매너의 핵심인 **'상대방에 대한 배려와 완곡한 표현(婉曲表現)'**은 아름다운 문화지만, 외국인 엔지니어에게는 행간에 숨겨진 진의(**本音, 혼네**)를 파악하는 데 높은 진입장벽이 되기도 합니다.

**KOTONA**는 AI(Gemini)를 활용하여 텍스트 뒤의 맥락을 분석하고, 비즈니스 매너 점수 산출 및 최적의 대응 문구를 제안함으로써 문화적 간극을 좁히고 업무 효율을 극대화하는 **'문화적 통역기'**입니다.

## 🛠 주요 기능 (Key Features)
- **Nuance Analysis**: 입력된 문장의 완곡함과 정중함을 분석하여 '진의(本音)' 도출.
- **Manner Scoring**: 일본 비즈니스 관습에 기반한 매너 점수 및 개선 포인트 가이드.
- **Smart Reply**: 상대방의 의도를 존중하면서도 명확한 의사를 전달하는 비즈니스 답안 초안 생성.

## ⚙️ 기술 스택 (Tech Stack)
- **Backend**: Java 17, Spring Boot 3.x, Spring Data JPA
- **AI**: Google Gemini API (Spring AI)
- **Database**: PostgreSQL / MySQL
- **Build Tool**: Gradle

## 🏗 아키텍처 (Architecture)
- 객체지향적 설계를 통한 분석 로직의 확장성 확보.
- 가독성 높은 API 명세 및 테스트 코드 중심 개발.

## ✅ Milestone
- **Phase 1**: Project Foundation & Backend Environment Setup
    - [x] Phase 1-1: Initialize GitHub Repository & Project Board
    - [x] Phase 1-2: Setup Spring Boot 3.x & Java 17 Development Environment
    - [x] Phase 1-3: Database Schema Design & Containerization (Docker with PostgreSQL/MySQL)
    - [x] Phase 1-4: Security Configuration (API Key Management & .env Setup)

- **Phase 2**: AI Integration & Core Analysis Engine Development
    - [] Phase 2-1: Integrate Google Gemini API via Spring AI
    - [] Phase 2-2: Design 'Role-based Prompts' for Japanese Business Context
    - [] Phase 2-3: Implement AI Response Parsing & Error Handling
    - [] Phase 2-4: Text Pre-processing & Japanese Token Analysis

- **Phase 3**: Core Business Logic & Scoring Algorithm
    - [] Phase 3-1: Develop Scoring Logic for 'Indirectness' and 'Etiquette'
    - [] Phase 3-2: Implement Sentiment Analysis for Extracting 'Honne'
    - [] Phase 3-3: Build Context-Aware Risk Detection (Soft-rejection signals)
    - [] Phase 3-4: Develop Category Classification Engine

- **Phase 4**: Response Generation & Data Management
    - [] Phase 4-1: Implement Smart Reply Generator for Various Scenarios
    - [] Phase 4-2: Develop CRUD APIs & Data Persistence for Analysis History
    - [] Phase 4-3: Construct Japanese Business Phrase Library
    - [] Phase 4-4: API Documentation Automation via Swagger

- **Phase 5**: Quality Assurance & Portfolio Finalization
    - [] Phase 5-1: Execute Unit Testing for Core Logic using JUnit5
    - [] Phase 5-2: Cloud Deployment & CI/CD Pipeline Configuration
    - [] Phase 5-3: Comprehensive Technical Documentation (README & Diagrams)
    - [] Phase 5-4: Final Project Retrospective & Achievement Summary
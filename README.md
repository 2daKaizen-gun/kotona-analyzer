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
- **Phase 1**: Foundation & Frontend Environment Setup
    - [x] Phase 1-1: Initialize GitHub Repository & Project Board
    - [x] Phase 1-2: Setup Next.js (App Router) & TypeScript Development Environment
    - [x] Phase 1-3: Configure Global Styling Strategy (Tailwind CSS & Business Theme)
    - [x] Phase 1-4: Security Setup (Environment Variables & .env Configuration)

- **Phase 2**: Holiday Data Acquisition & Modeling
    - [x] Phase 2-1: Research & Integrate Public Holiday APIs (KR/JP)
    - [x] Phase 2-2: Define TypeScript Interfaces for Holiday & Schedule Models
    - [x] Phase 2-3: Develop Data Fetching Utilities with Error Handling
    - [x] Phase 2-4: Implement Local Caching Logic for Performance Optimization

- **Phase 3**: Core Business & Comparison Logic
    - [x] Phase 3-1: Develop "Cross-Border Holiday Comparison" Engine
    - [x] Phase 3-2: Implement Long-term Vacation Detection (Golden Week, Chuseok, etc.)
    - [x] Phase 3-3: Build Context-Aware Alert Logic (Business Risk Assessment)
    - [x] Phase 3-4: Create Scheduling Recommendation Algorithm

- **Phase 4**: Advanced Web Interface & UX Development
    - [x] Phase 4-1: Build Interactive Dual-Calendar Dashboard UI
    - [x] Phase 4-2: Implement Real-time Schedule Conflict Visualization
    - [x] Phase 4-3: Develop Business Email Template Generator (KR/JP Bilingual)
    - [x] Phase 4-4: Implement Dynamic Calendar Navigation
    - [x] Phase 4-5: User-Defined Schedule Management & Data Persistence
    - [x] Phase 4-6: Integrate Gemini AI for Smart Email Generation

- **Phase 5**: Deployment, Documentation & Portfolio Finalization
    - [x] Phase 5-1: Cloud Deployment & CI/CD Pipeline Setup (Vercel)
    - [x] Phase 5-2: Performance Optimization (Server-side Rendering & Static Generation)
    - [x] Phase 5-3: Comprehensive Technical Documentation (README & API Docs)
    - [x] Phase 5-4: Global Localization & UX Optimization
    - [x] Phase 5-5: Code Refactoring & TypeScript Strict Mode Audit
    - [x] Phase 5-6: Final Project Retrospective
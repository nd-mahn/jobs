
---

**CONVENTION.md**
```markdown
# Project Conventions

## 📦 Module Structure
- jobs: Parent POM
- job-common: Common utilities
- job-core: Core logic
- job-lyrics-sync: Lyrics sync job

## 📝 Naming Conventions
- ArtifactId: job-<feature>
- Package: nd.mahn.<module>
- Class: PascalCase
- Method: camelCase
- Branch: feature/<module>-<short-desc>

## 🔖 Git Commit Conventions
- Format: [module] action: description
- Ví dụ: [job-core] fix: update destination config

## 🧪 Testing
- Unit test bắt buộc cho logic chính
- Đặt test trong `src/test/java` với hậu tố `Test`

## 📄 Documentation
- README.md: giới thiệu
- HELP.md: hướng dẫn build/run
- CONVENTION.md: quy tắc phát triển

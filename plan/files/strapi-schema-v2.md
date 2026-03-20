# PassTheo — Strapi CMS Content Schema v2 (with ProductType hierarchy)

> **Hierarchy:** Country → ProductType → Product → Domain → Topic → Question
> **Language:** Strapi i18n (nl, en, ar, tr, pl) — orthogonal to hierarchy
> **Design:** Product-agnostic. Adding VCA = new Strapi content only, zero code changes.

---

## Content Hierarchy

```
Country (NL, BE, DE)
  └── ProductType (CBR, VCA, WFT, Vaarbewijs)
        └── Product (Auto B, Motor A, VCA Basis)
              ├── ExamConfig (1:1 — exam rules per product)
              └── Domain (Verkeersborden, Voorrang...)
                    └── Topic (Voorrangsborden, Rotondes...)
                          ├── Question (6 interaction types)
                          └── Lesson (theory content)
```

**Language is NOT a hierarchy level.** A Turkish student in NL sees the same
CBR Auto Verkeersborden questions, translated to Turkish via Strapi i18n.

---

## Content Type 1: Country

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `name` | Text | Yes | Yes | "Nederland" |
| `code` | UID | Yes | No | "NL" — ISO 3166-1 alpha-2 |
| `flagImage` | Media | No | No | SVG flag |
| `defaultLocale` | Enum | Yes | No | nl, en, de, fr |
| `supportedLocales` | JSON | Yes | No | `["nl","en","ar","tr","pl"]` |
| `isActive` | Boolean | Yes | No | |
| `sortOrder` | Integer | Yes | No | |

**Relations:** has many ProductType, has many RoadSign

---

## Content Type 2: ProductType (NEW)

The certification system / regulatory body. CBR, VCA, WFT are completely
different worlds with different question formats and exam rules.

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `name` | Text | Yes | Yes | "CBR Rijexamen", "VCA Veiligheidscertificaat" |
| `code` | UID | Yes | No | "cbr", "vca", "wft", "vaarbewijs" |
| `description` | Rich text | No | Yes | |
| `icon` | Media | No | No | SVG |
| `coverImage` | Media | No | No | Hero image for selection screen |
| `regulatoryBody` | Text | No | No | "Centraal Bureau Rijvaardigheidsbewijzen" |
| `websiteUrl` | Text | No | No | "https://www.cbr.nl" |
| `isActive` | Boolean | Yes | No | |
| `sortOrder` | Integer | Yes | No | |

**Relations:** belongs to Country, has many Product

---

## Content Type 3: Product

The specific exam within a certification system. CBR Auto (B) and CBR Motor (A)
are different products — different question banks, different exam configs.

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `name` | Text | Yes | Yes | "Auto Theorie (B)", "Motor Theorie (A)" |
| `code` | UID | Yes | No | "auto-b", "motor-a", "vca-basis" |
| `licenceCode` | Text | No | No | "B", "A", "AM", "C" — replaces LicenceType |
| `description` | Rich text | No | Yes | |
| `icon` | Media | No | No | |
| `coverImage` | Media | No | No | |
| `isActive` | Boolean | Yes | No | |
| `isPremium` | Boolean | Yes | No | false = free product |
| `sortOrder` | Integer | Yes | No | |

**Relations:** belongs to ProductType, has one ExamConfig, has many Domain

**Composite unique:** Country.code + ProductType.code + Product.code
e.g. "NL/cbr/auto-b" uniquely identifies a question bank.

---

## Content Type 4: ExamConfig

One-to-one with Product. Defines the mock exam rules.

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `totalQuestions` | Integer | Yes | No | 50 for CBR Auto |
| `timeLimitMinutes` | Integer | Yes | No | 30 for CBR |
| `passScore` | Integer | Yes | No | 44 for CBR Auto (updated April 2025) |
| `extendedTimeLimitMinutes` | Integer | No | No | 45 for extra time candidates |
| `questionDistribution` | JSON | No | No | `{"knowledge":0.33,"application":0.67}` |
| `shuffleQuestions` | Boolean | Yes | No | default: true |
| `showFeedbackDuringExam` | Boolean | Yes | No | false for mock |
| `allowPause` | Boolean | Yes | No | false for mock |

**Relations:** belongs to Product (one-to-one)

---

## Content Type 5: Domain

Top-level subject groupings within a product. ~6 for CBR Auto.
Domains are NOT shared between products.

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `name` | Text | Yes | Yes | "Verkeersborden en verkeerstekens" |
| `code` | UID | Yes | No | "verkeersborden" |
| `slug` | UID | Yes | No | URL-safe |
| `description` | Text | No | Yes | |
| `icon` | Media | No | No | SVG |
| `color` | Text | No | No | Hex "#3B82F6" for UI cards |
| `questionCount` | Integer | No | No | |
| `isActive` | Boolean | Yes | No | |
| `isFreePreview` | Boolean | Yes | No | true = unlocked for free users |
| `sortOrder` | Integer | Yes | No | |

**Relations:** belongs to Product, has many Topic

**CBR Auto v1 Domains:**
1. Verkeersborden en verkeerstekens
2. Voorrang en kruisingen
3. Verkeersregels
4. Snelheid en afstand
5. Gevaarherkenning en rijgedrag
6. Voertuigkennis en milieu

---

## Content Type 6: Topic

Specific subjects within a domain. 5-10 per domain.

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `name` | Text | Yes | Yes | "Voorrangsborden", "Rotondes" |
| `code` | UID | Yes | No | |
| `slug` | UID | Yes | No | |
| `description` | Text | No | Yes | |
| `difficulty` | Enum | No | No | easy, medium, hard |
| `questionCount` | Integer | No | No | |
| `isActive` | Boolean | Yes | No | |
| `sortOrder` | Integer | Yes | No | |

**Relations:** belongs to Domain, has many Question, has many Lesson

---

## Content Type 7: Question

The 6 interaction types matching the real CBR exam (April 2025 format):

| Type Code | Dutch | Answer Mechanism |
|-----------|-------|-----------------|
| `multiple_choice` | Meerkeuzevraag | Select 1 from 2-4 options |
| `yes_no` | Ja/Nee vraag | Binary yes or no |
| `fill_in_number` | Invulvraag | Type a number |
| `tap_on_image` | Hotspot vraag | Tap region on image |
| `drag_checkmark` | Sleepvraag (enkel) | Drag to correct zone(s) |
| `drag_numbers` | Sleepvraag (cijfers) | Drag numbers in order |

| Field | Type | Required | i18n | Notes |
|-------|------|----------|------|-------|
| `questionText` | Rich text | Yes | Yes | The question |
| `interactionType` | Enum | Yes | No | 6 types above |
| `difficulty` | Enum | Yes | No | easy, medium, hard |
| `image` | Media | No | No | Traffic situation image |
| `video` | Media | No | No | Animated scenario (new CBR) |
| `videoUrl` | Text | No | No | External video URL |
| `correctNumber` | Integer | No | No | fill_in_number only |
| `correctNumberTolerance` | Integer | No | No | ±margin |
| `explanation` | Component | Yes | Yes | Why this answer is correct |
| `cbrReference` | Text | No | No | Official reference ID |
| `version` | Integer | Yes | No | Increment on edit, default: 1 |
| `isActive` | Boolean | Yes | No | |
| `isPremium` | Boolean | Yes | No | |

**Relations:** belongs to Topic, belongs to Domain (denormalized), belongs to Product (denormalized), has many AnswerOption (component), has many ImageRegion (component), has many DragTarget (component), many-to-many Tag

---

## Components (embedded in Question)

### AnswerOption
| Field | Type | Required | i18n |
|-------|------|----------|------|
| `text` | Text | Yes | Yes |
| `image` | Media | No | No |
| `isCorrect` | Boolean | Yes | No |
| `sortOrder` | Integer | Yes | No |

### Explanation
| Field | Type | Required | i18n |
|-------|------|----------|------|
| `text` | Rich text | Yes | Yes |
| `image` | Media | No | No |
| `tip` | Text | No | Yes |
| `relatedRoadSignCode` | Text | No | No |
| `legalReference` | Text | No | No |

### ImageRegion (for tap_on_image)
| Field | Type | Required | i18n |
|-------|------|----------|------|
| `label` | Text | No | Yes |
| `xPercent` | Decimal | Yes | No |
| `yPercent` | Decimal | Yes | No |
| `widthPercent` | Decimal | Yes | No |
| `heightPercent` | Decimal | Yes | No |
| `isCorrect` | Boolean | Yes | No |
| `sortOrder` | Integer | Yes | No |

### DragTarget (for drag types)
| Field | Type | Required | i18n |
|-------|------|----------|------|
| `label` | Text | Yes | Yes |
| `correctValue` | Text | No | No |
| `isCorrect` | Boolean | No | No |
| `sortOrder` | Integer | Yes | No |
| `image` | Media | No | No |

---

## Content Types 8-16 (Supporting)

### 8: Lesson
Theory content per topic. Fields: title, slug, content (rich text i18n), summary, coverImage, videoUrl, readTimeMinutes, isActive, isPremium, sortOrder. Belongs to Topic.

### 9: RoadSign
Dutch road sign reference. Fields: name (i18n), code, signCategory (enum), description (i18n), image (SVG), shape, isActive, sortOrder. Belongs to Country.

### 10: Achievement
Badge definitions. Fields: name (i18n), code, description (i18n), icon, lockedIcon, triggerType (enum), triggerValue, xpReward, isActive, sortOrder.

### 11: Tag
Cross-cutting labels. Fields: name (i18n), slug, color. Many-to-many with Question and RoadSign.

### 12: AppConfig (Singleton)
App settings. Fields: maintenanceMode, minimumAppVersion, freeDailyQuestionLimit (10), freeWeeklyExamLimit (1), defaultProductCode ("auto-b"), supportEmail, privacyPolicyUrl.

### 13: OnboardingSlide
Welcome screens. Fields: title (i18n), description (i18n), image, sortOrder, isActive.

### 14: Banner
Home screen promos. Fields: title (i18n), description (i18n), image, actionType, actionValue, startDate, endDate, isActive, sortOrder. Optional belongs to Product.

### 15: FAQ
Help content. Fields: question (i18n), answer (i18n), isActive, sortOrder.

### 16: NotificationTemplate
Email/push templates. Fields: code, channel (enum), subject (i18n), body (i18n), variables (JSON), isActive.

---

## Summary: 16 Content Types

| # | Type | Kind | Purpose |
|---|------|------|---------|
| 1 | Country | Collection | NL, BE, DE |
| 2 | **ProductType** | Collection | **CBR, VCA, WFT (NEW)** |
| 3 | Product | Collection | Auto B, Motor A, VCA Basis |
| 4 | ExamConfig | Collection | Exam rules per product |
| 5 | Domain | Collection | Subject groupings |
| 6 | Topic | Collection | Specific subjects |
| 7 | Question | Collection | Core content — 6 types |
| 8 | Lesson | Collection | Theory reading |
| 9 | RoadSign | Collection | Sign reference |
| 10 | Achievement | Collection | Gamification badges |
| 11 | Tag | Collection | Labels |
| 12 | AppConfig | Single | App settings |
| 13 | OnboardingSlide | Collection | Welcome flow |
| 14 | Banner | Collection | Promos |
| 15 | FAQ | Collection | Help |
| 16 | NotificationTemplate | Collection | Templates |

**Removed:** LicenceType (merged into Product.licenceCode)
**Added:** ProductType (new hierarchy level between Country and Product)

---

## v1 Seed Data

| Content | Count | Notes |
|---------|-------|-------|
| Country | 1 | NL |
| ProductType | 1 | CBR |
| Product | 1 | Auto B |
| ExamConfig | 1 | 50q, 30min, pass 44 |
| Domain | 6 | CBR Auto domains |
| Topic | 30-40 | 5-7 per domain |
| Question | 200+ (target 500) | NL + EN minimum |
| Lesson | 20-30 | 3-5 per domain |
| RoadSign | 50+ | Common Dutch signs |
| Achievement | 23 | All triggers |
| AppConfig | 1 | Defaults |
| OnboardingSlide | 4-5 | Welcome |
| NotificationTemplate | 8-10 | Core |

*Schema version: 2.0 — with ProductType hierarchy*
*CBR exam format: April 2025 (50q, 30min, pass 44/50)*

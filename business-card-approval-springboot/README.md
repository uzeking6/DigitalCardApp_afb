# Business Card Approval — Spring Boot / Java (converted from Flask)

This is a faithful 1:1 conversion of the original Flask app (`app.py`) to **Spring Boot 3 +
Java 21**. Every route and feature is preserved; only the underlying technology changed.

## Technology mapping (Flask → Spring Boot)

| Flask (Python) | Spring Boot (Java) |
|---|---|
| Flask routes (`@app.route`) | `@RestController` / `@Controller` (`PublicController`, `AdminController`) |
| Jinja2 templates | Thymeleaf templates (same `index.html`, `admin.html`, `login.html`) |
| SQLite (`cards.db`, `sqlite3`) | H2 file database + Spring Data JPA |
| `session[...]` (Flask session) | `HttpSession` |
| `hashlib.sha256` password | BCrypt (with automatic migration of old SHA-256 hashes) |
| `smtplib` / `MIMEMultipart` | Spring `JavaMailSender` (`EmailService`) |
| `openpyxl` (Excel build) | Apache POI (`VCardApiService.buildExcel`) |
| `requests.Session` → vCard API | `java.net.http.HttpClient` (`VCardApiService`) |
| `app.run(port=5000)` | embedded Tomcat on `server.port=5000` |
| `.env` via python-dotenv | environment variables (via `docker-compose` `.env`) |

## Route parity (all preserved)

| Flask route | Spring Boot mapping |
|---|---|
| `GET /` , `GET /demande` | `GET /`, `GET /demande` |
| `GET /login` | `GET /login` |
| `POST /submit` | `POST /submit` |
| `GET/POST /admin/login` | `GET /admin/login` + `POST /admin/authenticate` |
| `GET /admin/logout` | `GET /admin/logout` |
| `GET /admin` | `GET /admin` |
| `POST /admin/create-card` | `POST /admin/create-card` |
| `POST /approve/<id>` | `POST /approve/{id}` |
| `POST /reject/<id>` | `POST /reject/{id}` |
| `POST /update-status` | `POST /update-status` |
| `POST /admin/change-password` | `POST /admin/change-password` |
| `POST /check-status` | `POST /check-status` |
| `GET/POST /admin/smtp` | `GET/POST /admin/smtp` |
| `POST /admin/smtp/test` | `POST /admin/smtp/test` |
| `POST /admin/clear-all` | `POST /admin/clear-all` |
| (Excel download) | `GET /excel/{id}` |

## How to run (Docker — easiest)

```
docker compose up --build
```

Then open:
- Employee page (form + "Ma Carte" status): http://localhost:5000/demande
- Admin dashboard: http://localhost:5000/admin  → login `admin` / `afriland2024`

## How to run (without Docker)

Requires JDK 21 + Maven. Set the env vars from `.env` first, then:
```
mvn spring-boot:run
```
or build a jar:
```
mvn clean package -DskipTests
java -jar target/*.jar
```

## Configuration

All config is in `.env` (read by `docker-compose`):

- **Admin login**: `admin` / `APPROVAL_ADMIN_PASSWORD` (default `afriland2024`)
- **Email/SMTP**: set `SMTP_ENABLED=true` + `SMTP_USER` / `SMTP_PASS` (Gmail app password) /
  `SMTP_FROM`. The included `.env` already has working Gmail values — keep that file private.
- **vCard backend**: `VCARD_API_BASE` must point at your running `vcard-api` for the
  **approve → create card** step to actually push the card (same dependency the Flask app had).
  Everything else (form, status check, dashboard, reject, SMTP test, Excel) works standalone.

## Notes
- The H2 database file lives in `data/` (or the `approval_data` Docker volume) — it replaces
  `cards.db`. The schema (`requests`, `admin_credentials`, `smtp_config`) is created automatically
  on first run, and the default admin + SMTP config are seeded just like the Flask `init_db()`.
- If you change `APPROVAL_ADMIN_PASSWORD` or `SMTP_*` after the first run, reset the volume:
  `docker compose down -v && docker compose up --build`.

## ✅ Self-contained card page (no external backend needed)

This app now **creates and displays the card itself**:

- When the admin approves a request (or creates a card), the card becomes immediately
  viewable and the employee is emailed a **"Voir ma carte"** link.
- That link opens **`/carte?email=...`** — a real page served by THIS app showing the
  employee's info, a **QR code to scan**, phone numbers, and a **"Enregistrer le contact (vCard)"**
  button (`/carte/vcf?email=...` downloads a `.vcf`).
- No separate Angular viewer or `vcard-api` backend is required. (If you DO run one, set
  `VCARD_PUSH_ENABLED=true` to also push cards to it.)

New routes:
| Route | Purpose |
|---|---|
| `GET /carte?email=...` (alias `/card`) | The digital card page (info + QR + phones) |
| `GET /carte/vcf?email=...` | Downloads the contact as a `.vcf` |

Email sending no longer depends on any external service — it only needs SMTP enabled
(`SMTP_ENABLED=true` + your Gmail app password in `.env`), which is already set.

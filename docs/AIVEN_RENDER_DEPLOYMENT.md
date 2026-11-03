# Aiven and Render deployment notes

## Database configuration

WorkNest reads database settings from the process environment. It does not contain a database password in source code.

Set these values in the Tomcat Run Configuration in Eclipse and in the Render service environment:

- `DB_URL`: use the Aiven MySQL JDBC URL from the Aiven console, targeting `defaultdb` and including `sslMode=REQUIRED`.
- `DB_USERNAME`: the Aiven database username (`avnadmin` for the service described in the project setup).
- `DB_PASSWORD`: the current Aiven database password. Keep it in the environment settings only; do not commit it or paste it into source files.
- `GEMINI_API_KEY`: optional for project features that call Gemini. Set it as a secret in Render, never as a Docker build argument.

`DatabaseUtil` stops with a clear configuration error if any database value is missing, rejects non-MySQL URLs, and requires TLS mode `REQUIRED`.

The project SQL scripts define the same nine table names as the supplied schema dump: `users`, `projects`, `proposals`, `project_updates`, `project_submissions`, `client_profiles`, `freelancer_profiles`, `notifications`, and `project_invitations`. No schema was executed or changed as part of this migration. Do not import the dump into the existing Aiven database because it contains `DROP TABLE` statements. Confirm exact column definitions in Aiven before applying any future schema migration.

## Run locally from Eclipse

1. Open **Run Configurations** for the Tomcat server you use.
2. Under **Environment**, add `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Use the Aiven endpoint values from its console; do not copy the password into this document.
3. Set `DB_URL` to the Aiven MySQL connection URL, database `defaultdb`, with `sslMode=REQUIRED`.
4. Apply the settings and restart Tomcat and Eclipse if you changed Windows-level environment variables.
5. Open the application and check login, registration, dashboards, proposals, invitations, notifications, profiles, project updates, and delivery review.
6. If Aiven reports access denied, verify the active username and rotate/reset the database password in Aiven, then update the Eclipse environment entry. The application code cannot repair an invalid database credential.

## Deploy to Render

The root `render.yaml` and `Dockerfile` prepare a Docker-based Tomcat 9 service. The container compiles the project with Java 17, packages the existing `src/main/webapp` application, and binds Tomcat to Render's `PORT` value. The health check uses `/`.

1. Commit and push the project to your GitHub repository after reviewing staged files for secrets.
2. In Render, create a Blueprint from that repository, or create a Docker web service from the repository root.
3. Choose the Free plan if it is available for your account and region. Free services can sleep when idle and have usage limits; use this for a demo, not a production workload.
4. In the Render service environment, add `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `GEMINI_API_KEY` as runtime values. Do not put the password or API key in `render.yaml`, `Dockerfile`, or Git.
5. Deploy and review Render logs. A successful build does not prove the Aiven connection works; verify the app after deployment.
6. Test the same client and freelancer flows listed above, including AI drafting and proposal review if `GEMINI_API_KEY` is configured.

## Current verification status

The Java source compiled successfully with `javac --release 17`. A read-only Aiven connection check was attempted using `SELECT DATABASE()` and `SHOW TABLES`; Aiven rejected the configured credentials with an authentication error. No database writes or schema changes were performed. Update the active environment credentials and retry the connection check before deploying.
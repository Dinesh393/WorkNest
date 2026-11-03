# Aiven and Render deployment notes

WorkNest uses an external MySQL database and a Tomcat 9 Docker web service. The application reads connection settings from the process environment; it does not run database scripts during startup.

## Environment variables

Set these in Eclipse's Tomcat launch environment for local testing and in Render's service Environment settings for deployment:

- DB_URL: MySQL JDBC URL for the target database, including sslMode=REQUIRED. Example shape: jdbc:mysql://YOUR_HOST:YOUR_PORT/YOUR_DATABASE?sslMode=REQUIRED.
- DB_USERNAME: database account name.
- DB_PASSWORD: database password.
- GEMINI_API_KEY: optional secret for AI drafting and summaries.
- GEMINI_MODEL: optional model override.
- APP_ENV: set to `production` on Render to mark session cookies Secure; leave unset for local HTTP development.

Do not commit real values or put secrets in render.yaml, the Dockerfile, a Docker build argument, or frontend code. Render's sync: false entries avoid storing values in the Blueprint file. When creating a new Blueprint, enter the prompted values. For an existing service, set them in the service's Environment settings.

DatabaseUtil validates that the JDBC URL is MySQL and requires TLS mode REQUIRED. The application uses a Tomcat JDBC pool and closes it when the web application stops. Unsafe form and API requests are protected with session-bound CSRF tokens; Tomcat adds `SameSite=Lax` to the session cookie.

## Database setup

The SQL files in database/ are manual scripts. They are not run by Docker or Render. Review the target database and the complete script before execution. schema.sql is intended for a fresh local WorkNest database; feature scripts create additional tables. Do not apply the fresh-install schema over an existing database without confirming its current structure and backup plan.

remove_client_profile_verification.sql drops four columns from an older client_profiles table. It is a targeted, destructive migration, not part of deployment startup. Run it only when all named columns exist and removing them is intended. Back up the database first. No database migration or data change is performed by this project configuration.

## Run locally from Eclipse

1. Use JDK 17 and the Apache Tomcat 9 server configured for the project.
2. Open the Tomcat Run Configurations and add DB_URL, DB_USERNAME, and DB_PASSWORD under Environment.
3. Build DB_URL using the database host, port, database name, and sslMode=REQUIRED; keep the password in its separate environment variable.
4. Add GEMINI_API_KEY only if you want AI features.
5. Restart Tomcat after changing its environment settings.
6. Sign in and review client and freelancer flows. Check Tomcat logs for configuration or database connection errors; never paste secret values into logs or support messages.

## Render setup

The root render.yaml defines a Docker web service named worknest, a health check at /, and the DB_URL, DB_USERNAME, DB_PASSWORD, and GEMINI_API_KEY secret entries. The Docker build compiles the servlet sources for Java 17, deploys them to Tomcat 9's ROOT context, and the entrypoint configures Tomcat to listen on Render's PORT (default 10000 for local containers).

1. Push the reviewed project to GitHub, then create a Render Blueprint from the repository or create a Docker web service using the repository root.
2. Select the Free plan if offered for the account and region. Free web services can sleep while idle and have usage limits; expect a cold-start delay after inactivity.
3. Set the database environment values in Render. Use a MySQL provider that permits connections from Render and requires TLS. Keep the database separate from the web service.
4. Set GEMINI_API_KEY if AI features are desired. Without it, normal non-AI project workflows remain available; AI requests require the configured secret.
5. Review the deployment logs. A successful container build does not establish that database credentials, network access, or schema are correct.
6. Open the deployed site and verify registration/login, both dashboards, project posting, proposal submission and acceptance, invitations, profiles, notifications, project workspace, delivery review, and AI features if configured.

Do not run destructive SQL against a populated database as part of deployment. Confirm the live database schema and take a backup before any deliberate migration.
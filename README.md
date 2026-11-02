# WorkNest

WorkNest is a web application that connects clients with freelancers to post projects, review proposals, agree on work, and manage delivery in a shared project workspace.

## Features

### Client

- Register and sign in as a client.
- Create, update, view, and cancel projects.
- Draft project details from a short summary with Gemini AI, then review and edit the suggestions before posting.
- Review freelancer proposals and accept a proposal.
- Find freelancers using search, filters, sorting, and a saved shortlist.
- Send direct project invitations with an offer amount and optional message.
- Track project progress, review submitted work, and approve it or request a revision.
- Receive notifications linked to relevant project pages.

### Freelancer

- Register and sign in as a freelancer.
- Create and update a professional profile.
- Browse open projects and submit proposals.
- Track proposal and invitation statuses.
- Accept or decline direct client invitations.
- Share progress updates and submit final work in the project workspace.
- Receive notifications about proposal and project activity.

## Technology

- Java Servlets
- HTML, CSS, and JavaScript
- MySQL
- MySQL Connector/J
- Apache Tomcat 9
- Eclipse IDE for Enterprise Java and Web Developers
- Gemini Developer API for optional AI project drafting (free-tier model; usage limits apply)

The Eclipse project is currently configured with a JavaSE-25 classpath. The Java version can be adjusted to match the JDK installed in the development environment.

## Project structure

```text
WorkNest/
├── database/                  # Feature-specific SQL setup scripts
├── docs/screenshots/           # Project screenshots displayed below
├── src/main/java/              # Servlets, filters, and utilities
└── src/main/webapp/            # HTML pages, stylesheets, scripts, and WEB-INF
    ├── dashboard/client/
    ├── dashboard/freelancer/
    ├── css/
    ├── js/
    └── WEB-INF/
```

## Screenshots

Add screenshots using the filenames shown below. Once the image files are added to `docs/screenshots/`, GitHub will display them here.

### Client dashboard

![WorkNest client dashboard](docs/screenshots/client-dashboard.png)

### Client project and proposals

![WorkNest client project](docs/screenshots/client-project.png)

### Freelancer dashboard

![WorkNest freelancer dashboard](docs/screenshots/freelancer-dashboard.png)

### Freelancer profile

![WorkNest freelancer profile](docs/screenshots/freelancer-profile.png)

### Project workspace

![WorkNest project workspace](docs/screenshots/project-workspace.png)

## Requirements

- Eclipse IDE with Web Tools Platform (WTP)
- A JDK compatible with the project compiler setting
- Apache Tomcat 9
- MySQL Server
- MySQL database named `worknest_db`

## Database

The application connects to `jdbc:mysql://localhost:3306/worknest_db`. Database access uses the `DB_USERNAME` and `DB_PASSWORD` environment variables. Configure these for the Tomcat process and restart Tomcat after changing them. Do not store credentials in source files or commit them to Git.

The `database/` folder contains the core schema and supporting feature scripts. For a fresh database, run:

- `schema.sql` creates the database and core tables for users, projects, proposals, progress updates, and deliveries. It creates tables only and inserts no data.
- `notifications.sql` creates the notifications table.
- `freelancer_profiles.sql` creates the freelancer profiles table.
- `project_invitations.sql` creates the direct-hiring invitations table.

The last three scripts add optional supporting tables used by notifications, freelancer profiles, and direct invitations. Run them after `schema.sql` when using those features.

## Running locally

1. Start MySQL and run `database/schema.sql`.
2. Run the supporting SQL scripts listed above.
3. Import the project into Eclipse using **File → Import → Existing Projects into Workspace**.
4. Set the project JDK and Apache Tomcat 9 runtime in Eclipse.
5. Configure `DB_USERNAME` and `DB_PASSWORD` in the Tomcat launch configuration's Environment settings.
6. To use AI project drafting, add `GEMINI_API_KEY` to the same Tomcat Environment settings. The draft feature remains optional; the project form can be completed manually without the key.
7. Start Tomcat and open `http://localhost:8080/WorkNest/` in a browser.

The port and context path may differ depending on the local Tomcat configuration.

## Main workflows

1. A client creates an open project.
2. A freelancer finds the project and submits a proposal, or the client sends a direct invitation.
3. The client accepts a proposal, or the freelancer accepts the invitation.
4. The project moves into progress and both users use the project workspace.
5. The freelancer posts updates and submits the completed work.
6. The client approves the delivery or requests a revision.

## Security notes

- Keep database passwords, API credentials, private keys, and certificates out of the repository.
- Gemini free-tier requests are subject to model availability and usage limits. Project summaries are sent to Google to generate draft details, so avoid entering confidential information.
- Use environment variables or a local configuration excluded by `.gitignore` for secrets.
- Use test data in screenshots and remove personal account information before publishing.
- Review the files staged for each commit before pushing to GitHub.



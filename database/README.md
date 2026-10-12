# Database Setup

Phase 2 uses MySQL 8 with the database `campus_complaint_management`. The local MySQL service was found running on port `3306`; the JDBC URL defaults to:

```text
jdbc:mysql://localhost:3306/campus_complaint_management
```

The URL can be overridden with `DB_URL`. The username defaults to `root` and can be overridden with `DB_USERNAME`. A password is required from `DB_PASSWORD`; no password is stored in the project.

## Windows CMD Setup

In the same CMD terminal where the backend will run, set the variables (replace the placeholder locally; do not commit it):

```cmd
set "DB_USERNAME=root"
set "DB_PASSWORD=<your-local-MySQL-password>"
set "DB_URL=jdbc:mysql://localhost:3306/campus_complaint_management"
```

Create the database if it does not already exist. The MySQL client prompts for the password; the password is not an argument:

```cmd
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS campus_complaint_management CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

If the MySQL client is not on `PATH`, use its installed full path or create the database through MySQL Workbench. Do not change users/passwords or disable authentication to work around a connection error.

Then start the backend from the `backend` folder:

```cmd
mvn test
mvn package
mvn spring-boot:run
```

The application applies the versioned Flyway migration and Hibernate validates the resulting schema. It does not drop tables. If migration startup fails because existing tables conflict with the migration, inspect and back up the schema rather than deleting data.

## Implemented Schema

The initial migration creates `departments`, `users`, `complaint_categories`, `campus_locations`, `complaints`, `complaint_assignments`, and `complaint_status_history`. See [DATABASE_DESIGN.md](../docs/DATABASE_DESIGN.md) for field and relationship notes.

Automated repository tests use isolated in-memory H2 and do not touch MySQL. The database health endpoint performs a real connection check when the application runs.

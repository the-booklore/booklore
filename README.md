# BookLore

![GitHub release (latest by date)](https://img.shields.io/github/v/release/adityachandelgit/BookLore?color=green)
![License](https://img.shields.io/github/license/adityachandelgit/BookLore?color=orange)
![Stars](https://img.shields.io/github/stars/adityachandelgit/BookLore?style=social)
![Docker Pulls](https://img.shields.io/docker/pulls/booklore/booklore?color=2496ED)
[![Join us on Discord](https://img.shields.io/badge/Chat-Discord-5865F2?logo=discord&style=flat)](https://discord.gg/Ee5hd458Uz)
[![Open Collective backers and sponsors](https://img.shields.io/opencollective/all/booklore?label=Open%20Collective&logo=opencollective&color=7FADF2)](https://opencollective.com/booklore)
[![Venmo](https://img.shields.io/badge/Venmo-Donate-008CFF?logo=venmo)](https://venmo.com/AdityaChandel)

> 🚨 **Important Announcement:**  
> Docker images have moved to new repositories:
> - Docker Hub: `https://hub.docker.com/r/booklore/booklore`
> - GitHub Container Registry: `https://ghcr.io/booklore-app/booklore`
>
> The legacy repo (`https://ghcr.io/adityachandelgit/booklore-app`) will remain available for existing images but will not receive further updates.


BookLore is a self-hosted web app for organizing and managing your personal book collection. It provides an intuitive interface to browse, read, and track your progress across PDFs and eBooks. With robust metadata management, multi-user support, and a sleek, modern UI, BookLore makes it easy to
build and explore your personal library.

![BookLore Demo](assets/demo.gif)

## ✨ Key Features

- 📚 **Smart Organization**: Build your dream library with custom shelves, smart sorting, and powerful filters to find any book instantly
- 👥 **Multi-User Management**: Add users with granular permissions for library access and content control
- 📲 **Kobo Integration**: Seamlessly sync your library with Kobo devices and convert EPUBs to KEPUB automatically
- ✨ **Magic Shelves**: Create dynamic, rule-based smart collections that auto-update in real time - like smart playlists for your books
- 🧠 **Auto Metadata**: Fetch rich book details from Goodreads, Amazon, Google Books, and Hardcover
- 📤 **BookDrop Import**: Drop files in a folder for automatic detection and bulk import
- 🌐 **OPDS Support**: Connect reading apps directly to your library for wireless downloads
- 🔑 **Flexible Authentication**: Choose between local accounts or external OIDC providers (Authentik, Pocket ID)
- 🔄 **KOReader Sync**: Track reading progress across KOReader and BookLore
- 📧 **One-Click Sharing**: Send books via email directly from the interface
- 🔐 **Private Notes**: Save personal reading notes visible only to you
- 🌍 **Community Reviews**: Auto-fetch public reviews to enrich your library
- 📖 **Built-in Reader**: Read PDFs, EPUBs, and comics with customizable themes
- 📱 **Mobile Ready**: Fully responsive design optimized for all devices

## 💖 Support the Project

If you find **BookLore** helpful, please consider supporting its development:

- ⭐ Star this repository to show your appreciation and help others discover it.
- 💸 Contribute via [Open Collective](https://opencollective.com/booklore) to help fund development, hosting, and testing costs.
  > 📌 Currently raising funds for a **Kobo device** to implement and test native Kobo sync support.  
  > 💡 [Support the Kobo Sync Bounty →](https://opencollective.com/booklore/projects/kobo-device-for-testing)
- ⚡ Prefer one-time support? You can also donate via [Venmo](https://venmo.com/AdityaChandel).

## 🌐 Live Demo: Explore BookLore in Action

Evaluate BookLore’s features and user experience in a live environment:

**Demo Access:**

- 🌐 **URL:** [demo.booklore.dev](https://demo.booklore.dev)
- 👤 **Username:** `booklore`
- 🔑 **Password:** `9HC20PGGfitvWaZ1`

> ⚠️ **Note:**
> The demo account has standard user permissions only.
> - **Admin features** (user management, library setup, advanced configuration) are not available in this demo.
> - To explore all capabilities, including administration and multi-user management, please deploy your own instance as described below.

## 🚀 Getting Started with BookLore

Kick off your BookLore journey with our official documentation and helpful video guides.

📘 [BookLore Documentation: Getting Started](https://booklore-app.github.io/booklore-docs/docs/getting-started/)  
Our up-to-date docs walk you through installation, setup, configuration, and key features, everything you need to get up and running smoothly.

> 💡 **Want to improve the documentation?**  
> You can update the docs at [booklore-app/booklore-docs](https://github.com/booklore-app/booklore-docs) and create a pull request to contribute your changes!

🎥 [BookLore Tutorials: YouTube](https://www.youtube.com/watch?v=UMrn_fIeFRo&list=PLi0fq0zaM7lqY7dX0R66jQtKW64z4_Tdz)  
These older videos provide useful walkthroughs and visual guidance, but note that some content may be outdated compared to the current docs.

## 🐳 Deploy with Docker

You can quickly set up and run BookLore using Docker.

### 1️⃣ Install Docker & Docker Compose

Ensure you have [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) installed.

**Image Repositories:**

- Docker Hub: `https://hub.docker.com/r/booklore/booklore`
- GitHub Container Registry: `https://ghcr.io/booklore-app/booklore`

> **Note:** Legacy images under `https://ghcr.io/adityachandelgit/booklore-app` will remain available but will not receive new updates.

### 2️⃣ Create docker-compose.yml

> ⚠️ If you intend to run the container as a non-root user, you must manually create all of your `/your/local/path/to/booklore` directories with read and write permissions for your intended user **before first run**.

Create a `docker-compose.yml` file with content:

```yaml
services:
  booklore:
    # Official Docker Hub image:
    image: booklore/booklore:latest
    # Or the GHCR image:
    # image: ghcr.io/booklore-app/booklore:latest
    container_name: booklore
    environment:
      - USER_ID=0
      - GROUP_ID=0
      - TZ=Etc/UTC
      - DATABASE_URL=jdbc:mariadb://mariadb:3306/booklore
      - DATABASE_USERNAME=booklore                          # Must match MYSQL_USER defined in the mariadb container
      - DATABASE_PASSWORD=your_secure_password              
      - BOOKLORE_PORT=6060                                  # Port BookLore listens on inside the container; must match container port below
    depends_on:
      mariadb:
        condition: service_healthy
    ports:
      - "6060:6060" # HostPort:ContainerPort → Keep both numbers the same, and also ensure the container port matches BOOKLORE_PORT, no exceptions. 
      # All three (host port, container port, BOOKLORE_PORT) must be identical for BookLore to function properly.
      # Example: To expose on host port 7070, set BOOKLORE_PORT=7070 and use "7070:7070". 
    volumes:
      - /your/local/path/to/booklore/data:/app/data       # Application data (settings, metadata, cache, etc.). Persist this folder to retain your library state across container restarts.
      - /your/local/path/to/booklore/books:/books         # Primary book library folder. Mount your collection here so BookLore can access and organize your books.
      - /your/local/path/to/booklore/bookdrop:/bookdrop   # BookDrop folder. Files placed here are automatically detected and prepared for import.
    restart: unless-stopped

  mariadb:
    image: lscr.io/linuxserver/mariadb:11.4.5
    container_name: mariadb
    environment:
      - PUID=1000
      - PGID=1000
      - TZ=Etc/UTC
      - MYSQL_ROOT_PASSWORD=super_secure_password  # Use a strong password for the database's root user, should be different from MYSQL_PASSWORD
      - MYSQL_DATABASE=booklore
      - MYSQL_USER=booklore                        # Must match DATABASE_USERNAME defined in the booklore container
      - MYSQL_PASSWORD=your_secure_password        # Use a strong password; must match DATABASE_PASSWORD defined in the booklore container
    volumes:
      - /your/local/path/to/mariadb/config:/config
    restart: unless-stopped
    healthcheck:
      test: [ "CMD", "mariadb-admin", "ping", "-h", "localhost" ]
      interval: 5s
      timeout: 5s
      retries: 10
```

Note: You can find the latest BookLore image tag `BOOKLORE_IMAGE_TAG` (e.g. v.0.x.x) from the Releases section:
📦 [Latest Image Tag – GitHub Releases](https://github.com/adityachandelgit/BookLore/releases)

### 3️⃣ Start the Containers

Run the following command to start the services:

```ini
docker compose up -d
```

### 4️⃣ Access BookLore

Once the containers are up, access BookLore in your browser at:

```ini
http : //localhost:6060
```

## ⚙️ Supported Environment Variables

### BookLore Container

| Key                  | Default Value                          | Description                                                                                                                                                                                                                                                        |
|----------------------|----------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `USER_ID`            | `0`                                    | User ID for file ownership within the container. Set this to match your host user's UID if running as non-root (e.g., `1000`). Use `id -u` on Linux to find your UID.                                                                                              |
| `GROUP_ID`           | `0`                                    | Group ID for file ownership within the container. Set this to match your host user's GID if running as non-root (e.g., `1000`). Use `id -g` on Linux to find your GID.                                                                                             |
| `TZ`                 | `Etc/UTC`                              | Timezone for the application. Controls timestamps in logs and UI. Use standard timezone identifiers (e.g., `America/New_York`, `Europe/London`, `Asia/Tokyo`). See [List of tz database time zones](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones). |
| `DATABASE_URL`       | `jdbc:mariadb://mariadb:3306/booklore` | JDBC connection string for MariaDB/MySQL database. Format: `jdbc:mariadb://hostname:port/database_name`. Only modify if using a custom database host, port, or external database server.                                                                           |
| `DATABASE_USERNAME`  | `booklore`                             | Username for database authentication. Must match `MYSQL_USER` in your MariaDB container configuration.                                                                                                                                                             |
| `DATABASE_PASSWORD`  | -                                      | Password for database authentication. **Required**. Use a strong, unique password. Must match `MYSQL_PASSWORD` in your MariaDB container configuration. Store securely and never commit to version control.                                                        |
| `BOOKLORE_PORT`      | `6060`                                 | Internal port BookLore listens on. **Critical**: This must match both the host and container ports in your port mapping (e.g., `6060:6060`). Changing this requires updating all three values. Example: For port `7070`, use `BOOKLORE_PORT=7070` and `7070:7070`. |
| `SWAGGER_ENABLED`    | `false`                                | Controls access to Swagger UI for API documentation and testing. Set to `true` to enable at `/swagger-ui.html` (useful for development/testing). Keep `false` in production for security.                                                                          |
| `FORCE_DISABLE_OIDC` | `false`                                | Forces local username/password authentication only. Set to `true` to completely disable OIDC/OAuth2 providers (Authentik, Pocket ID, etc.). Useful for troubleshooting authentication issues or temporarily bypassing external identity providers.                 |

### MariaDB Container

| Key                   | Default Value | Description                                                                                                                                                                                                      |
|-----------------------|---------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PUID`                | `1000`        | Process User ID for the LinuxServer.io MariaDB container. Set this to match your host user's UID for proper file permissions. Use `id -u` on Linux to find your UID.                                            |
| `PGID`                | `1000`        | Process Group ID for the LinuxServer.io MariaDB container. Set this to match your host user's GID for proper file permissions. Use `id -g` on Linux to find your GID.                                           |
| `TZ`                  | `Etc/UTC`     | Timezone for the MariaDB container. Should match the BookLore container's timezone for consistency in timestamps.                                                                                                |
| `MYSQL_ROOT_PASSWORD` | -             | Root password for the MariaDB database. **Required**. Use a strong, unique password different from `MYSQL_PASSWORD`. This provides administrative access to the database server. Never expose or commit to VCS. |
| `MYSQL_DATABASE`      | `booklore`    | Name of the database to create on first run. This database will be used by BookLore to store all application data. Should not be changed after initial setup.                                                   |
| `MYSQL_USER`          | `booklore`    | Username for the application database user. Must match `DATABASE_USERNAME` in the BookLore container configuration. This user will have full access to the `MYSQL_DATABASE`.                                     |
| `MYSQL_PASSWORD`      | -             | Password for the application database user. **Required**. Use a strong, unique password. Must match `DATABASE_PASSWORD` in the BookLore container configuration. Store securely and never commit to VCS.         |

## 📥 Bookdrop Folder: Auto-Import Files

BookLore now supports a **Bookdrop folder**, a special directory where you can drop your book files (`.pdf`, `.epub`, `.cbz`, etc.), and BookLore will automatically detect, process, and prepare them for import. This makes it easy to bulk add new books without manually uploading each one.

### 🔍 How It Works

1. **File Watcher:** A background process continuously monitors the Bookdrop folder.
2. **File Detection:** When new files are added, BookLore automatically reads them and extracts basic metadata (title, author, etc.) from filenames or embedded data.
3. **Optional Metadata Fetching:** If enabled, BookLore can query metadata sources like Google Books or Open Library to enrich the book information.
4. **Review & Finalize:** You can then review the detected books in the Bookdrop UI, edit metadata if needed, and assign each book to a library and folder structure before finalizing the import.

### ⚙️ Configuration (Docker Setup)

To enable the Bookdrop feature in Docker:

```yaml
services:
  booklore:
    ...
    volumes:
      - /your/local/path/to/booklore/data:/app/data
      - /your/local/path/to/booklore/books:/books
      - /your/local/path/to/booklore/bookdrop:/bookdrop # 👈 Bookdrop directory
```

## 🔑 OIDC/OAuth2 Authentication (Authentik, Pocket ID, etc.)

BookLore supports optional OIDC/OAuth2 authentication for secure access. This feature allows you to integrate external authentication providers for a seamless login experience.

While the integration has been tested with **Authentik** and **Pocket ID**, it should work with other OIDC providers like **Authelia** as well. The setup allows you to use either JWT-based local authentication or external providers, giving users the flexibility to choose their preferred method.

For detailed instructions on setting up OIDC authentication:

- 📺 [YouTube video on configuring Authentik with BookLore](https://www.youtube.com/watch?v=r6Ufh9ldF9M)
- 📘 [Step-by-step setup guide for Pocket ID](docs/OIDC-Setup-With-PocketID.md)

## 🛡️ Forward Auth with Reverse Proxy

BookLore also supports **Forward Auth** (also known as Remote Auth) for authentication through reverse proxies like **Traefik**, **Nginx**, or **Caddy**. Forward Auth works by having your reverse proxy handle authentication and pass user information via HTTP headers to BookLore. This can be set up
with providers like **Authelia** and **Authentik**.

For detailed setup instructions and configuration examples:

- 📘 [Complete Forward Auth Setup Guide](docs/forward-auth-with-proxy.md)

## 🤝 Community & Support

- 🐞 Found a bug? [Open an issue](https://github.com/adityachandelgit/BookLore/issues)
- ✨ Want to contribute? [Check out CONTRIBUTING.md](https://github.com/adityachandelgit/BookLore/blob/master/CONTRIBUTING.md)
- 💬 **Join our Discord**: [Click here to chat with the community](https://discord.gg/Ee5hd458Uz)

## 👨‍💻 Contributors & Developers

Thanks to all the amazing people who contribute to Booklore.

[![Contributors List](https://contrib.rocks/image?repo=adityachandelgit/BookLore)](https://github.com/adityachandelgit/BookLore/graphs/contributors)

## ⭐ Star History

<a href="https://www.star-history.com/#booklore-app/booklore&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=booklore-app/booklore&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=booklore-app/booklore&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=booklore-app/booklore&type=date&legend=top-left" />
 </picture>
</a>

## ⚖️ License

* [GNU GPL v3](http://www.gnu.org/licenses/gpl.html)
* Copyright 2024-2025

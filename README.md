# NasaDaily

NasaDaily is a Java 21 desktop application for fetching, downloading, and managing NASA's **Astronomy Picture of the Day (APOD)**. It ships with both a full-featured CLI and a dark-themed Swing GUI, backed by a local SQLite cache to prevent redundant API calls.

---

## Features

| Feature | Description |
|---|---|
| **Fetch Today's APOD** | Download the APOD for the current date in one command |
| **Historical Lookup** | Retrieve any single past APOD by date (`YYYY-MM-DD`) |
| **Bulk / Range Download** | Import an entire date range in a single operation |
| **Smart Caching** | SQLite-backed cache — already-fetched entries are never re-downloaded |
| **Rate Limiting** | Configurable delay between downloads to respect API constraints |
| **Automated Scheduling** | Keep a persistent process running that imports the APOD daily at a set time |
| **HD Image Preference** | Automatically prefers the `hdurl` field over the standard URL when available |
| **Video Support** | Non-image APODs (e.g. YouTube embeds) are saved as `.url` shortcut files |
| **Swing GUI** | Dark-themed graphical interface with image preview, calendar-based import, and embedded log viewer |

---

## Requirements

- **Java 21** or higher
- **Maven 3.6+** (for building from source)
- A NASA API key — the default `DEMO_KEY` works but is rate-limited to 30 requests/hour. Get a free key at [api.nasa.gov](https://api.nasa.gov/).

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/nebalus/NasaDaily.git
cd NasaDaily
```

### 2. Build the Project

The compiled `target/` directory is not tracked in version control, so you must build the project yourself using Maven:

```bash
mvn clean package
```

This compiles all 12 source files and produces the runnable JAR at:

```
target/NasaDaily-1.0.0.jar
```

> You can also use `mvn clean install` to install the artifact into your local Maven repository.

### 3. Run the Application

See the **[Usage](#usage)** section below.

---

## Usage

### Command Line Interface (CLI)

The CLI is the default mode. All operations are performed by passing flags to the JAR.

```bash
# Fetch and download today's APOD
java -jar target/NasaDaily-1.0.0.jar

# Download a single historical APOD by date
java -jar target/NasaDaily-1.0.0.jar --date 2024-01-01

# Download all APODs in a date range
java -jar target/NasaDaily-1.0.0.jar --from 2024-01-01 --to 2024-01-31

# Download from a date up to today (omit --to)
java -jar target/NasaDaily-1.0.0.jar --from 2024-01-01

# Run as a daily background scheduler (fetches at 08:00 every day)
java -jar target/NasaDaily-1.0.0.jar --schedule 08:00

# Customize the rate limit between downloads (default is 30 seconds)
java -jar target/NasaDaily-1.0.0.jar --rate-limit 10 --from 2024-01-01 --to 2024-01-05

# Launch the graphical user interface
java -jar target/NasaDaily-1.0.0.jar --gui
```

#### CLI Flag Reference

| Flag | Argument | Description |
|---|---|---|
| *(none)* | — | Imports today's APOD |
| `--date` | `YYYY-MM-DD` | Imports a single historical entry |
| `--from` | `YYYY-MM-DD` | Start of a date range import |
| `--to` | `YYYY-MM-DD` | End of the range (defaults to today if omitted) |
| `--schedule` | `HH:mm` | Starts a persistent daily scheduler at the given time |
| `--rate-limit` | `<seconds>` | Seconds to wait between image downloads (default: `30`) |
| `--gui` | — | Launches the Swing GUI instead of running CLI mode |

---

### Graphical User Interface (GUI)

```bash
java -jar target/NasaDaily-1.0.0.jar --gui
```

The GUI opens a `1200×800` dark-themed window with three main areas:

- **Image panel** (center): Displays the currently selected APOD image with its title and explanation.
- **Side panel** (right): Contains local image navigation (◄/►), an interactive calendar for selecting import date ranges, a rate-limit spinner, and an "Import Today" button.
- **Log panel** (bottom): A monospaced live log output showing all import activity, with a "Clear" button and a progress bar for batch operations.

The calendar highlights already-downloaded dates so you can instantly see which entries are missing from your local library.

---

## Output Structure

Downloaded files are saved to the `apod_images/` directory, organized by year and month:

```
apod_images/
└── 2024/
    ├── 01/
    │   ├── 2024-01-01_Nebula_Title.jpg
    │   └── 2024-01-15_Some_Video.url
    └── 02/
        └── 2024-02-20_Galaxy_Cluster.png
```

- **Images** are saved with their HD resolution URL when available.
- **Video APODs** are saved as `.url` files (Windows Internet Shortcut format), containing the link to the video.
- File names follow the pattern: `YYYY-MM-DD_Sanitized_Title.ext`

---

## Caching

NasaDaily uses a local **SQLite database** (`.apod_cache/apod.db`) to cache all fetched API metadata. This means:

- Re-running the same command never makes a redundant API call.
- Range imports only fetch the *missing* dates — already-cached entries are served instantly.
- The GUI calendar uses the cache to highlight which dates have been imported.

---

## Technologies Used

| Library | Purpose |
|---|---|
| **Java** | Core language (records, streams, `java.time`) |
| **Swing** | Graphical user interface |
| **SQLite JDBC** (`org.xerial`) | Local metadata cache storage |
| **org.json** | Parsing NASA API JSON responses |
| **JLogger** (`dev.nebalus.library`) | Structured, colored console and GUI logging |

---

## Configuration

The following constants are hardcoded in `ApodCore.java` and `FetcherUI.java` and can be changed before building:

| Constant | Default | Description |
|---|---|---|
| `API_KEY` | `"DEMO_KEY"` | NASA API key — replace with your own for higher rate limits |
| `OUTPUT_DIR` | `apod_images/` | Directory where downloaded images are saved |
| `CACHE_DIR` | `.apod_cache/` | Directory containing the SQLite cache database |

---

## License

This project is open-source and available under the terms of the **MIT License**.

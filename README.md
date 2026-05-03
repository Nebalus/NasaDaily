# APODFetcher

APODFetcher is a Java 21 application that allows you to easily fetch, download, and manage NASA's Astronomy Picture of the Day (APOD). It provides both a powerful Command Line Interface (CLI) and a user-friendly Graphical User Interface (GUI) to browse and download astronomical images.

## Features

- **Fetch Today's APOD**: Quickly download the Astronomy Picture of the Day for the current date.
- **Historical Downloads**: Fetch a specific APOD from any valid past date.
- **Bulk Downloading**: Download a range of APODs between two specified dates.
- **Automated Scheduling**: Run the application in the background to automatically fetch the APOD at a specific time every day.
- **Rate Limiting**: Built-in rate limiting to respect API constraints.
- **Local Caching**: Uses a local SQLite database to cache downloaded entries and prevent redundant API calls.
- **Graphical Interface**: Includes a responsive Swing-based GUI with an image preview panel, integrated log display, and intuitive controls for date navigation and batch imports.

## Requirements

- Java 21 or higher
- Maven (for building the project)

## Getting Started

### Building the Project

Use Maven to build the project and download the required dependencies:

```bash
mvn clean install
```

### Usage

You can run the application either in CLI mode or GUI mode.

#### Launching the GUI

```bash
java -jar target/APODFetcher-0.0.1-SNAPSHOT.jar --gui
```

#### Command Line Interface

```bash
# Import today's APOD
java -jar target/APODFetcher-0.0.1-SNAPSHOT.jar

# Import a single historical APOD
java -jar target/APODFetcher-0.0.1-SNAPSHOT.jar --date 2024-01-01

# Import a date range
java -jar target/APODFetcher-0.0.1-SNAPSHOT.jar --from 2024-01-01 --to 2024-01-31

# Run daily import at the specified time (e.g., 8:00 AM)
java -jar target/APODFetcher-0.0.1-SNAPSHOT.jar --schedule 08:00

# Set a custom rate limit (seconds between downloads, default is 30)
java -jar target/APODFetcher-0.0.1-SNAPSHOT.jar --rate-limit 15 --from 2024-01-01 --to 2024-01-05
```

## Technologies Used

- **Java 21**: Core programming language.
- **Swing**: For the Graphical User Interface.
- **SQLite JDBC**: For local metadata and cache storage.
- **org.json**: For parsing the NASA API responses.
- **JLogger**: For structured and colored console/file logging.

## License

This project is open-source and available under the terms of the MIT License.

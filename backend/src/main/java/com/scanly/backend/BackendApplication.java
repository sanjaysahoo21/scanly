package com.scanly.backend;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	static {
		// Spring's test bootstrap does not invoke main(), so initialize UTC when
		// the application class is loaded as well as during normal startup.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		loadDotenv();
	}

	public static void loadDotenv() {
		String[] searchPaths = {"./", "../"};
		for (String path : searchPaths) {
			File envFile = new File(path, ".env");
			if (envFile.exists()) {
				try {
					Dotenv dotenv = Dotenv.configure().directory(path).ignoreIfMissing().load();
					for (DotenvEntry entry : dotenv.entries()) {
						if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
							System.setProperty(entry.getKey(), entry.getValue());
						}
					}
				} catch (Exception ignored) {
				}
			}
		}
	}

	public static void main(String[] args) {
		loadDotenv();
		SpringApplication.run(BackendApplication.class, args);
	}

}

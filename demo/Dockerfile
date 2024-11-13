# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory to /app
WORKDIR /app

# Copy the packaged JAR file into the container at /app
COPY target/vrp-0.0.1-SNAPSHOT.jar app.jar

# Copy the required OSM file into the container
COPY src/main/resources/osm/laos-latest.osm.pbf /app/osm/laos-latest.osm.pbf

# Make port 8080 available to the world outside this container
EXPOSE 8080

# Run the JAR file
CMD ["java", "-jar", "app.jar"]

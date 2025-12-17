## Running Instructions

### Build the Project
All Java components are built using Maven.  
This step creates executable fat JARs for the OPC-UA server and agents.

```
  mvn clean package 
```
or use the maven plugin on the right panel - "Lifecycle" - "clean" then "package" (if using IntelliJ )


### After a successful Maven build, start all services using Docker Compose:

``` 
docker compose build
docker compose up -d
```
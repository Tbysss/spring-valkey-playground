# playground

## jinja2 templating docker compose file

Install jinja2 and jinja2-cli in python virtual env:
```aiignore
python -m venv ./.venv
source ./.venv/bin/activate
pip install jinja2 jinja2-cli
```
Compile template to create a valid docker compose file:
```aiignore
jinja2 templates/docker-compose.valkey-cluster.yaml.j2 templates/cluster.json > docker-compose.yaml
```

### Configuration
Jinja2 configuration at template/cluster.json. Change `with_app` to `true` if running full containerized setup.

```aiignore
jinja2 -D with_app=true templates/docker-compose.valkey-cluster.yaml.j2 templates/cluster.json > docker-compose.yaml
```

## Builing the app

```aiignore
./gradlew bootBuildImage
```

## Running the playground

```aiignore
docker compose up -d
```
Wait a few seconds for the cluster to initialize.

## Running demo

```aiignore
curl -X POST http://localhost:8080/demo8
```
Parameters:
```aiignore
# can save with different strategy for comparision
# 0: uses CRUD interface
# 1: uses key value adapater (which should be the same as crud, but we can parallize it)
# 2: uses custom pipelined key value adapter (reuses same connection)
?strategy=2
# number of items to save
?numItems=50
```
Example query to save 100 items with the key value adapter strategy:
```aiignore
curl -X POST http://localhost:8080/demo8?strategy=1&numItems=100
```

## Monitoring
View logs with `docker compose logs -f app`

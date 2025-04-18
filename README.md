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
jinja2 -o docker-compose.valkey-cluster.yaml templates/docker-compose.valkey-cluster.yaml.j2 templates/cluster.json
```

## builing the app

```aiignore
./gradlew bootBuildImage
```

## running the playground

```aiignore
docker compose -f docker-compose.valkey-cluster.yaml up -d
```
Wait a few seconds for the cluster to initialize.

## Running demo

```aiignore
curl -X POST http://localhost:8080/demo8
```

View logs with `docker compose -f docker-compose.valkey-cluster.yaml logs -f app`

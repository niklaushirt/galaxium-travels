#!/bin/bash
podman machine start

podman login quay.io -u niklaushirt@gmail.com

cd rgalaxium-travels

export CONT_VERSION=1.0

cd booking_system_backend
podman buildx build --platform linux/amd64 -t quay.io/niklaushirt/galaxium-backend:$CONT_VERSION --load -f=Dockerfile
podman push quay.io/niklaushirt/galaxium-backend:$CONT_VERSION
cd -


cd booking_system_frontend
podman buildx build --platform linux/amd64 -t quay.io/niklaushirt/galaxium-frontend:$CONT_VERSION --load -f=Dockerfile
podman push quay.io/niklaushirt/galaxium-frontend:$CONT_VERSION
cd -

cd booking_system_inventory_hold_service
podman buildx build --platform linux/amd64 -t quay.io/niklaushirt/galaxium-hold-service:$CONT_VERSION --load -f=Dockerfile
podman push quay.io/niklaushirt/galaxium-hold-service:$CONT_VERSION
cd -


oc apply -f ./scripts/K8s/kubernetes.yaml
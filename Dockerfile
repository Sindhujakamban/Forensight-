# Build the Spring Boot backend.
FROM maven:3.9-eclipse-temurin-25 AS java-build
WORKDIR /app
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package

# JRE 25 is copied into the Python runtime image so Java and the original
# CPU-only TruFor inference stack run in the same Render web service.
FROM eclipse-temurin:25-jre AS java-runtime

FROM python:3.10-slim-bookworm AS runtime
ARG TRUFOR_COMMIT=ae54475df6f41a491d7615100feb19263dec13f7
ARG TRUFOR_WEIGHTS_URL=https://www.grip.unina.it/download/prog/TruFor/TruFor_weights.zip
ARG TRUFOR_WEIGHTS_MD5=7bee48f3476c75616c3c5721ab256ff8
ARG TRUFOR_WEIGHTS_DRIVE_ID=1iDogtuwsyMb1sVf6dBNXZQViz0f36-a_

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:$PATH
ENV PORT=8080
ENV PYTHONUNBUFFERED=1
ENV OMP_NUM_THREADS=1
ENV MKL_NUM_THREADS=1
ENV OPENBLAS_NUM_THREADS=1
ENV TRUFOR_ENABLED=true
ENV TRUFOR_ROOT=/opt/trufor
ENV TRUFOR_PYTHON=python
ENV TRUFOR_BRIDGE=/app/backend/trufor/trufor_bridge.py
ENV TRUFOR_OUTPUT_DIR=/app/uploads/trufor
ENV MPLCONFIGDIR=/tmp/matplotlib

COPY --from=java-runtime /opt/java/openjdk /opt/java/openjdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates curl git unzip libgomp1 libglib2.0-0 libgl1 libsm6 libxext6 libxrender1 libfontconfig1 libfreetype6 libx11-6 libxi6 libxtst6 libasound2 \
    && rm -rf /var/lib/apt/lists/*

# Pin the CPU PyTorch version used by the upstream TruFor inference container.
# Render does not provide an NVIDIA GPU, so inference explicitly runs with -gpu -1.
RUN python -m pip install --no-cache-dir --upgrade pip \
    && python -m pip install --no-cache-dir \
       torch==1.11.0+cpu torchvision==0.12.0+cpu \
       --extra-index-url https://download.pytorch.org/whl/cpu \
    && python -m pip install --no-cache-dir \
       numpy==1.21.5 \
       timm==0.5.4 \
       yacs==0.1.8 \
       tqdm==4.64.1 \
       Pillow==9.3.0 \
       matplotlib==3.5.3

# Add only the official lightweight inference source tree and retain both
# upstream license files inside the deployed image. Do not put weights in Git.
RUN git clone --depth 1 https://github.com/grip-unina/TruFor.git /tmp/TruFor \
    && git -C /tmp/TruFor fetch --depth 1 origin "$TRUFOR_COMMIT" \
    && git -C /tmp/TruFor checkout --detach "$TRUFOR_COMMIT" \
    && mkdir -p /opt/trufor/src /opt/trufor/weights \
    && cp -a /tmp/TruFor/test_docker/src/. /opt/trufor/src/ \
    && cp /tmp/TruFor/test_docker/LICENSE.txt /opt/trufor/LICENSE.txt \
    && cp /tmp/TruFor/test_docker/LICENSE_CMX.txt /opt/trufor/LICENSE_CMX.txt \
    && printf 'Official source: https://github.com/grip-unina/TruFor\nPinned commit: %s\n' "$TRUFOR_COMMIT" > /opt/trufor/UPSTREAM.txt \
    && rm -rf /tmp/TruFor

# Fetch the official pretrained archive, falling back to the download mirror
# published by a GRIP-UNINA collaborator in the upstream issue tracker.
# Verify the upstream-published MD5 before extracting the model.
RUN set -eux; \
    WEIGHTS_ZIP=/tmp/TruFor_weights.zip; \
    if ! curl --fail --location --retry 3 --retry-delay 3 "$TRUFOR_WEIGHTS_URL" --output "$WEIGHTS_ZIP"; then rm -f "$WEIGHTS_ZIP"; fi; \
    if [ ! -s "$WEIGHTS_ZIP" ] || [ "$(md5sum "$WEIGHTS_ZIP" | cut -d ' ' -f 1)" != "$TRUFOR_WEIGHTS_MD5" ]; then \
      rm -f "$WEIGHTS_ZIP"; \
      python -m pip install --no-cache-dir gdown==5.2.0; \
      python -m gdown --fuzzy "https://drive.google.com/file/d/$TRUFOR_WEIGHTS_DRIVE_ID/view?usp=sharing" --output "$WEIGHTS_ZIP"; \
    fi; \
    echo "$TRUFOR_WEIGHTS_MD5  $WEIGHTS_ZIP" | md5sum --check -; \
    unzip -q "$WEIGHTS_ZIP" -d /opt/trufor/weights; \
    MODEL_FILE="$(find /opt/trufor/weights -type f -name 'trufor.pth.tar' -print -quit)"; \
    test -n "$MODEL_FILE"; \
    if [ "$MODEL_FILE" != "/opt/trufor/weights/trufor.pth.tar" ]; then mv "$MODEL_FILE" /opt/trufor/weights/trufor.pth.tar; fi; \
    test -s /opt/trufor/weights/trufor.pth.tar; \
    rm -f "$WEIGHTS_ZIP"

WORKDIR /app
COPY --from=java-build /app/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY backend/trufor/trufor_bridge.py /app/backend/trufor/trufor_bridge.py
RUN mkdir -p /app/uploads/trufor

EXPOSE 8080
ENTRYPOINT ["java", "-Xms256m", "-Xmx512m", "-jar", "/app/app.jar"]

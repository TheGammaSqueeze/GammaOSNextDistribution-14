FROM node:12
ARG userid
ARG groupid
ARG username
ARG tzbbroot

RUN apt-get update && apt-get install -y gdal-bin zip

# Set up the user so that files are owned by the proper user
RUN groupadd -g $groupid $username \
 && useradd -m -u $userid -g $groupid $username
RUN mkdir -p $tzbbroot && chown $userid $tzbbroot
ENV DOCKER=true
USER $username


No PostgreSQL client .deb packages were bundled into this tarball.

To include psql for Debian/Ubuntu (amd64) targets, on a machine with Internet run:

  ./scripts/download-psql-for-bundle.sh
  ./scripts/build-offline-bundle.sh

Then redeploy the new logmng-offline-*.tar.gz. On the air-gapped server, ./install-offline.sh db
will try to install bundled .deb files when psql is missing (requires root/sudo and dpkg).

For RHEL/Rocky/Alma, install the postgresql client from your own offline RPM repository.

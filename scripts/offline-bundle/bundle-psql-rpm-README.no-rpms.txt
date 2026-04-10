No PostgreSQL client RPM packages were bundled into this tarball.

To include psql for RHEL / Rocky / Alma 9.6 (x86_64) from PGDG, on a machine with Internet run:

  ./scripts/download-psql-rpm-el9.sh
  ./scripts/build-offline-bundle.sh

Then redeploy the new logmng-offline-*.tar.gz. On the air-gapped server, ./install-offline.sh db
or ./install-offline.sh install-psql will try to install bundled .rpm files when psql is missing
(requires root/sudo and dnf or yum).

For Debian/Ubuntu, use scripts/download-psql-for-bundle.sh (tools/psql-deb).

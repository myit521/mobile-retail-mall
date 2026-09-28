#!/bin/sh

_sky_grant_flyway_metadata() {
  # MYSQL_USER is interpolated as an identifier below, so reject every
  # character outside the deliberately narrow Compose username contract.
  case "${MYSQL_USER:-}" in
    ''|*[!A-Za-z0-9_]*)
      echo 'MYSQL_USER must contain only letters, digits, and underscores.' >&2
      return 1
      ;;
  esac

  MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-}" mysql --protocol=socket --user=root <<SQL
GRANT SELECT ON performance_schema.user_variables_by_thread TO \`$MYSQL_USER\`@'%';
SQL
}

_sky_grant_flyway_metadata
_sky_grant_status=$?
unset -f _sky_grant_flyway_metadata
if [ "$_sky_grant_status" -ne 0 ]; then
  unset _sky_grant_status
  return 1 2>/dev/null || exit 1
fi
unset _sky_grant_status

# Exports the Virtuoso prod DB to ttl

echo "Exporting Virtuoso PROD graphs as ttl files; run as scoreboard user"
file_name=prod_export_graph

read -s -p "Enter Virtuoso dba password:" virtuoso_pass
echo

rm -rf /tmp/$file_name/ /tmp/$file_name.tgz
mv /var/local/scoreboard/download/$file_name.tgz /var/local/scoreboard/download/${file_name}_old.tgz
mkdir /tmp/$file_name

isql 1111 dba $virtuoso_pass add_dump_procedures.sql

isql 1111 dba $virtuoso_pass export_prod.sql
pushd .
cd /tmp
tar czf $file_name.tgz $file_name/

mv $file_name.tgz /var/local/scoreboard/download/

if [ -f /var/local/scoreboard/download/$file_name.tgz ]
then
  echo "The export is available at http://localhost/scoreboard/download/$file_name.tgz"
  rm -rf /tmp/$file_name/ /tmp/$file_name.tgz
else
  echo "There was a problem exporting; restoring the previous version."
  mv /var/local/scoreboard/download/${file_name}_old.tgz /var/local/scoreboard/download/${file_name}.tgz
fi
popd



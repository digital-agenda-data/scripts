package ro.edw;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.*;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.sql.*;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Synchronizes two graphs
 * Only one way, without removing the old destination data (only updating the data if needed)
 */
public class SynchronizeVirtuoso {

    private final Log log = LogFactory.getLog(SynchronizeVirtuoso.class);
    String dbUsername;
    String dbPassword;
    String dbUrl;
    /**
     * Map for original data triples, concatenated for easy search
     */
    Map<String, String> originalTriples;
    /**
     * Map for original data subject and predicate (useful for new/updated data)
     */
    Map<String, List<String>> originalSP;
    private String endpoint;
    private List<String> ignoreMultiple = new ArrayList<>();
    private List<String> doNotUpdate = new ArrayList<>();
    private String separator = "*#*";
    private boolean listMultiple = false;
    private boolean logOnly = true;

    /**
     * Create the virtuoso synchronizer
     * @param jdbcUrl
     * @param username
     * @param password
     */
    public SynchronizeVirtuoso(String endpointUrl, String jdbcUrl, String username, String password) {
        this.endpoint = endpointUrl;
        this.dbUrl = jdbcUrl;
        this.dbUsername = username;
        this.dbPassword = password;
    }

    public static void main(String[] args) throws Exception {

        Properties prop = new Properties();
        InputStream in = SynchronizeVirtuoso.class.getResourceAsStream("/virtuoso.properties");
        prop.load(in);
        in.close();

        String[] ignoreMultiple = prop.getProperty("ignore_multiple","").split("\\s*,\\s*");
        String[] doNotUpdate = prop.getProperty("do_not_update","").split("\\s*,\\s*");

        SynchronizeVirtuoso jt = new SynchronizeVirtuoso(prop.getProperty("source.sparql_endpoint"),
                prop.getProperty("dest.jdbc"),
                prop.getProperty("dest.username"),
                prop.getProperty("dest.password"));

        jt.setIgnoreMultiple(Arrays.asList(ignoreMultiple));
        jt.setDoNotUpdate(Arrays.asList(doNotUpdate));
        jt.setListMultiple(Boolean.parseBoolean(prop.getProperty("dest.show_multiples", "false")));
        jt.setLogOnly(Boolean.parseBoolean(prop.getProperty("log_only","true")));

        String[] graphs = prop.getProperty("graphs").split("\\s*,\\s*");
        for(String graph : graphs) {
            jt.syncrhonize(graph);
        }

    }

    /**
     * Construct query string for the graph
     * @param graph
     * @return
     */
    private String getQueryString(String graph){
         return "construct  {  ?s ?p ?o } " +
                " where { " +
                "  graph <" + graph + "> { ?s ?p ?o } " +
                "}";
    }

    /**
     * Initialize the old data maps
     * @param graph
     */
    private void initOldData(String graph){
        Connection conn = null;
        Statement st = null;
        originalTriples = new HashMap<>();
        originalSP = new HashMap<>();

        try {
            conn = getDBConnection();
            st = conn.createStatement();
            // Read the original data to decide if it's an insert or update
            ResultSet rs = st.executeQuery("sparql " + getQueryString(graph));
            while (rs.next()) {
                String s = rs.getString(1);
                String p = rs.getString(2);
                String o = rs.getString(3);

                log.debug("OLD: " + s + "  " + p + "  " + o + "  " + rs.getObject(3).getClass().getName());
                originalTriples.put(s + separator + p + separator + o, o);
                List<String> l = originalSP.get(s + separator + p);


                if (rs.getObject(3) instanceof java.sql.Timestamp) {
                    // todo this does not seem to be working
                    // format timestamp
//                    "2015-12-08T18:52:33+02:00"^^xsd:dateTime
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                    o = '\"' + sdf.format(rs.getTimestamp(3)) + '\"' + "^^xsd:dateTime";
                    log.debug("format " + rs.getObject(3) + " = " + o);
                }

                if (l == null) {
                    l = new ArrayList<>();
                    originalSP.put(s + separator + p, l);
                }
                l.add(o);
            }

        } catch (Exception e){
            log.error(e, e);
        } finally {
            try {
                if(st != null) st.close();
                if(conn != null) conn.close();
            } catch (Exception ignore) {log.debug(ignore, ignore); }
        }
    }

    /**
     * Multiplicity checks for original data
     * Optional, does not influence the import
     */
    private void checkMultiple(){
        for (String key : originalSP.keySet()) {
            List<String> set = originalSP.get(key);
            boolean doIgnore = false;
            if (set.size() > 1) {
                for (String ignore : ignoreMultiple) {
                    if (key.contains(ignore)) {
                        doIgnore = true;
                    }
                }
                if (!doIgnore) {
                    log.warn("More than one value for " + key.replace(separator, "  "));
                    for (String s : set) {
                        log.info("  " + s);
                    }
                }
            }
        }
    }

    /**
     * Initializes a DB connection
     * @return
     * @throws Exception
     */
    private Connection getDBConnection() throws Exception {
        Class.forName("virtuoso.jdbc4.Driver");
        Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        if(conn != null){
            return conn;
        }
        else {
            throw new Exception("Could not init DB connection");
        }
    }

    /**
     * Reads the triples from the source (SPARQL query)
     * @param graph
     * @param endpoint
     * @return
     */
    private Iterator<Triple> getSourceTriples(String graph, String endpoint){
        Query query = QueryFactory.create(getQueryString(graph), Syntax.syntaxARQ);
        QueryExecution queryExecution = QueryExecutionFactory.sparqlService(endpoint, query);
        Iterator<Triple> results = queryExecution.execConstructTriples();
        queryExecution.close();
        return results;
    }

    /**
     * Start the graph synchronization
     * @param graph
     * @throws Exception
     */
    public void syncrhonize(String graph) throws Exception {

        log.info("Synchronizing graph " + graph);

        initOldData(graph);

        if(listMultiple){
            checkMultiple();
        }

        Connection conn = getDBConnection();

        log.info("Original triples: " + originalTriples.size());

        try {
            int found = 0;
            int total = 0;
            int different = 0;

            Iterator<Triple> results = getSourceTriples(graph, endpoint);
            while (results.hasNext()) {
                Triple t = results.next();
                total++;

                String inOriginal = originalTriples.get(t.getSubject() + separator + t.getPredicate() + separator + t.getObject().getIndexingValue());
                if (inOriginal == null) {

                    // The exact value does not exist in the target
                    List<String> l = originalSP.get(t.getSubject() + separator + t.getPredicate());
                    if (l == null) {
                        // and S,P does not have other values
                        log.info("NOT FOUND " + t.getSubject() + "  " + t.getPredicate() + "  " + t.getObject());
                        insert(graph, t, conn);

                    } else {

                        if (isIgnorePredicateMultiple(t.getPredicate().toString())) {
                            // insert the value even if multiple
                            insert(graph, t, conn);
                        } else if (l.size() == 1) {
                            if (!isDoNotUpdate(t.getPredicate().toString())) {
                                // update the value
                                log.info("TO UPDATE " + t.getSubject() + "  " + t.getPredicate() + "  -" + t.getObject() + "-  ORIGINAL -" + l.get(0) + "-");

                                if (t.getObject().isLiteral() && t.getObject().getLiteralDatatype() != null) {
                                    log.debug(" TYPE: " + t.getObject().getLiteralDatatype());
                                }

                                delete(graph, l.get(0), t, conn);
                                insert(graph, t, conn);
                            }
                        } else {
                            log.warn("MULTIPLE VALUES " + t.getSubject() + "  " + t.getPredicate() + "  " + t.getObject());
                            for (String s : l) {
                                log.debug(" ORIGINAL: " + s);
                            }
                        }

                        different++;
                    }
                } else {
                    found++;
                    log.debug("OK: " + t.getSubject() + "  " + t.getPredicate() + "  " + t.getObject());
                }
            }
            log.info("Found " + found + "/" + total + " and " + different + " different");
        } catch(Exception e) {
            log.error(e, e);
        } finally {
            if(conn != null){
                conn.close();
            }
        }
    }

    /**
     * Returns true if the given predicate is in the ignoreMultiple list
     * @param predicate
     * @return
     */
    private boolean isIgnorePredicateMultiple(String predicate) {
        return ignoreMultiple.contains(predicate);
    }

    /**
     * Returns true if the given predicate is in the doNotUpdate list
      * @param predicate
     * @return
     */
    private boolean isDoNotUpdate(String predicate) {
        return doNotUpdate.contains(predicate);
    }

    /**
     * Insert a triple
     * @param graph
     * @param t
     * @param conn
     */
    private void insert(String graph, Triple t, Connection conn) {
        if(logOnly) return;
        Statement insert = null;
        try {
            insert = conn.createStatement();
            String s = null;
            if (t.getObject().isLiteral()) {
                s = t.getObject().getLiteral().getValue().toString();
            } else {
                s = t.getObject().toString();
            }
            s = escape(s);

            log.info("   FIX: insert " + s);
            int result = insert.executeUpdate("sparql INSERT INTO GRAPH <" + graph + "> { <" + t.getSubject() + "> <" + t.getPredicate() + "> \"" + s + "\" }");
            insert.close();
            if (result != 1) {
                log.warn("   WARN: insert failed");
            }

        } catch (Exception e) {
            log.error(e, e);
        } finally {
            if (insert != null) {
                try {
                    insert.close();
                } catch (SQLException e1) {
                    log.error(e1, e1);
                }
            }
        }
    }

    /**
     * Delete a triple (using the old value)
     * @param graph
     * @param oldValue
     * @param t
     * @param conn
     */
    private void delete(String graph, String oldValue, Triple t, Connection conn) {
        if(logOnly) return;
        Statement delete = null;

        try {
            delete = conn.createStatement();

            log.info("   FIX: delete " + oldValue);

            if (!oldValue.contains("^^xsd:dateTime")) {
                oldValue = "\"" + escape(oldValue) + "\"";
            }

//            int result = delete.executeUpdate("sparql DELETE FROM GRAPH <" + graph + "> { <" + t.getSubject() + "> <" + t.getPredicate() + "> " + oldValue + " }");
            String deleteSQL = "sparql\n" +
                    "delete {\n" +
                    "  GRAPH <" + graph + "> {\n" +
                    "\n" +
                    "  <" + t.getSubject() + ">\n" +
                    "  <" + t.getPredicate() + ">\n " +
                    oldValue +
                    "  }\n" +
                    "}";
            System.out.println(deleteSQL);
            int result = delete.executeUpdate(deleteSQL);
            delete.close();
            if (result != 1) {
                log.warn("   WARN: delete failed");
            }

        } catch (Exception e) {
            log.error(e, e);
        } finally {
            if (delete != null) {
                try {
                    delete.close();
                } catch (SQLException e1) {
                    log.error(e1, e1);
                }
            }
        }
    }

    /**
     * Escape a string to be used as a triple object
     * @param s
     * @return
     */
    private String escape(String s) {
        String result = StringEscapeUtils.escapeJava(s);
        result = result.replaceAll("'", "\\\\'");
        return result;
    }

    public void setIgnoreMultiple(List<String> ignoreMultiple) {
        this.ignoreMultiple = ignoreMultiple;
    }

    public void setDoNotUpdate(List<String> doNotUpdate) {
        this.doNotUpdate = doNotUpdate;
    }

    public void setListMultiple(boolean listMultiple) {
        this.listMultiple = listMultiple;
    }

    public void setLogOnly(boolean logOnly) {
        this.logOnly = logOnly;
    }
}

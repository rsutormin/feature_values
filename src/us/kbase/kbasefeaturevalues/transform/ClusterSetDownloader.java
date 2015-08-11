package us.kbase.kbasefeaturevalues.transform;

import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Arrays;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import us.kbase.auth.AuthService;
import us.kbase.auth.AuthToken;
import us.kbase.kbasefeaturevalues.ClusterSet;
import us.kbase.kbasefeaturevalues.LabeledCluster;
import us.kbase.workspace.ObjectIdentity;
import us.kbase.workspace.WorkspaceClient;

public class ClusterSetDownloader {
    
    public static void main(String[] args) throws Exception {
        Args parsedArgs = new Args();
        CmdLineParser parser = new CmdLineParser(parsedArgs);
        parser.setUsageWidth(100);
        if (args.length == 0 || (args.length == 1 && (args[0].equals("-h") || 
                args[0].equals("--help")))) {
            parser.parseArgument();
            showUsage(parser, null, System.out);
            return;
        }
        try {
            parser.parseArgument(args);
        } catch (CmdLineException ex) {
            String message = ex.getMessage();
            showUsage(parser, message);
            return;
        }
        String user = System.getProperty("test.user");
        String pwd = System.getProperty("test.pwd");
        String tokenString = System.getenv("KB_AUTH_TOKEN");
        AuthToken token = tokenString == null ? AuthService.login(user, pwd).getToken() :
            new AuthToken(tokenString);
        String outputFileName = parsedArgs.outName;
        if (outputFileName == null) {
            outputFileName = "clusters.";
            if (parsedArgs.format != null) {
                if (parsedArgs.format.equalsIgnoreCase("TSV") || parsedArgs.format.equalsIgnoreCase("SIF")) {
                    outputFileName += parsedArgs.format.toLowerCase();
                } else {
                    throw new IllegalStateException("Unsupported output format: " + parsedArgs.format);
                }
            } else {
                outputFileName += "tsv";
            }
        }
        File outputFile = new File(parsedArgs.workDir, outputFileName);
        generate(parsedArgs.wsUrl, parsedArgs.wsName, parsedArgs.objName, parsedArgs.version, 
                parsedArgs.format, token, new PrintWriter(outputFile));
    }
    
    public static void generate(String wsUrl, String wsName, String objName, Integer version,
            String format, AuthToken token, PrintWriter pw) throws Exception {
        try {
            boolean isTsv = format == null || format.equalsIgnoreCase("TSV");
            WorkspaceClient client = getWsClient(wsUrl, token);
            String ref = wsName + "/" + objName;
            if (version != null)
                ref += "/" + version;
            ClusterSet data = client.getObjects(Arrays.asList(new ObjectIdentity().withRef(ref)))
                    .get(0).getData().asClassInstance(ClusterSet.class);
            for (int clustPos = 0; clustPos < data.getFeatureClusters().size(); clustPos++) {
                LabeledCluster cluster = data.getFeatureClusters().get(clustPos);
                for (String featureId : cluster.getIdToPos().keySet()) {
                    if (isTsv) {
                        pw.println(featureId + "\t" + clustPos);
                    } else {
                        pw.println(featureId.replace(' ', '_') + " pc " + clustPos);
                    }
                }
            }
        } finally {
            pw.close();
        }
    }

    private static WorkspaceClient getWsClient(String wsUrl, AuthToken token) throws Exception {
        WorkspaceClient wsClient = new WorkspaceClient(new URL(wsUrl), token);
        wsClient.setAuthAllowedForHttp(true);
        return wsClient;
    }

    private static void showUsage(CmdLineParser parser, String message) {
        showUsage(parser, message, System.err);
    }
    
    private static void showUsage(CmdLineParser parser, String message, PrintStream out) {
        if (message != null)
            out.println(message);
        out.println("Program downloads cluster set data in TSV and SIF formats.");
        out.println("Usage: <program> [options...]");
        out.println("   Or: <program> {-h|--help}  - to see this help");
        parser.printUsage(out);
    }

    public static class Args {
        @Option(name="-ws", aliases={"--workspace_service_url"}, usage="Workspace service URL", metaVar="<ws-url>")
        String wsUrl;
        
        @Option(name="-wn", aliases={"--workspace_name"}, usage="Workspace name", metaVar="<ws-name>")
        String wsName;

        @Option(name="-on", aliases={"--object_name"}, usage="Object name", metaVar="<obj-name>")
        String objName;

        @Option(name="-ov", aliases={"--version"}, usage="Object version (optional)", metaVar="<obj-ver>")
        Integer version;

        @Option(name="-wd", aliases={"--working_directory"}, usage="Working directory", metaVar="<work-dir>")
        File workDir;

        @Option(name="-of", aliases={"--output_file_name"}, usage="Output file name", metaVar="<out-file>")
        String outName;

        @Option(name="-ff", aliases={"--format"}, usage="Output file format (one of TSV or SIF, default is TSV)", metaVar="<file-fmt>")
        String format;
    }

}

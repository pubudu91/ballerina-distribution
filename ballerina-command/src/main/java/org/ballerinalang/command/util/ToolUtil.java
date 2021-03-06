/*
 * Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ballerinalang.command.util;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.ballerinalang.command.Main;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Ballerina tool utilities.
 */
public class ToolUtil {
   // public static final String PRODUCTION_URL = "https://api.central.ballerina.io/1.0/update-tool";
    public static final String PRODUCTION_URL = "https://api.staging-central.ballerina.io/update-tool";
    public static final String BALLERINA_TYPE = "jballerina";

    private static TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    //No need to implement.
                }
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                    //No need to implement.
                }
            }
    };

    /**
     * Provides used Ballerina version.
     * @return Used Ballerina version
     */
    public static String getCurrentBallerinaVersion() throws IOException {
        return getVersion(OSUtils.getBallerinaVersionFilePath());
    }

    private static void setCurrentBallerinaVersion(String version) throws IOException {
        setVersion(OSUtils.getBallerinaVersionFilePath(), version);
    }

    private static void clearCache(PrintStream outStream) throws IOException {
        OSUtils.clearBirCacheLocation(outStream);
        OSUtils.clearJarCacheLocation(outStream);
    }

    /**
     * Provides used Ballerina tools version.
     * @return Used Ballerina tools version.
     */
    public static String getCurrentToolsVersion() throws IOException {
        InputStream inputStream = Main.class.getResourceAsStream("/META-INF/tool.properties");
        Properties properties = new Properties();
        properties.load(inputStream);
        return properties.getProperty("ballerina.command.version");

    }

    private static String getVersion(String path) throws IOException {
        BufferedReader br = Files.newBufferedReader(Paths.get(path));
        List<String> list = br.lines().collect(Collectors.toList());
        return list.get(0).replace(BALLERINA_TYPE + "-", "");
    }

    public static void setVersion(String path, String version) throws IOException {
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        if (!version.contains(BALLERINA_TYPE)) {
            version = BALLERINA_TYPE  + "-" + version;
        }

        writer.println(version);
        writer.close();
    }

    public static boolean use(PrintStream printStream, String distribution) {
        try {
            File installFile = new File(getDistributionsPath() + File.separator + distribution);
            if (installFile.exists()) {
                if (distribution.equals(getCurrentBallerinaVersion())) {
                    printStream.println(distribution + " is already in use ");
                    return true;
                } else {
                    setCurrentBallerinaVersion(distribution);
                    clearCache(printStream);
                    printStream.println("Using " + distribution);
                    return true;
                }
            }
        } catch (IOException | URISyntaxException e) {
            printStream.println("Cannot use " + distribution);
        }

        return false;
    }

    public static List<Distribution> getDistributions() throws IOException, KeyManagementException,
            NoSuchAlgorithmException {

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        List<Distribution> distributions = new ArrayList<>();
        URL url = new URL(PRODUCTION_URL + "/distributions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("user-agent",
                OSUtils.getUserAgent(getCurrentBallerinaVersion(),
                        getCurrentToolsVersion(), "jballerina"));
        conn.setRequestProperty("Accept", "application/json");
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            throw new RuntimeException("Failed : HTTP error code : "
                    + conn.getResponseCode());
        } else {
            String json = convertStreamToString(conn.getInputStream());
            Pattern p = Pattern.compile("\"version\":\"(.*?)\"");
            Matcher matcher = p.matcher(json);
            while (matcher.find()) {
                distributions.add(new Distribution(BALLERINA_TYPE, matcher.group(1)));
            }
        }
        conn.disconnect();
        return distributions;
    }

    public static String getLatest(String currentVersion, String type)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {
        String version = currentVersion;
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());


        URL url = new URL(PRODUCTION_URL
                + "/distributions/latest?version=" + currentVersion + "&type=" + type);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("user-agent",
                OSUtils.getUserAgent(getCurrentBallerinaVersion(),
                        getCurrentToolsVersion(), "jballerina"));
        conn.setRequestProperty("Accept", "application/json");
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            throw new RuntimeException("Failed : HTTP error code : "

                    + conn.getResponseCode());
        } else {
            version = getValue(type, convertStreamToString(conn.getInputStream()));
        }
        conn.disconnect();
        return version;
    }

    private static String getValue(String key, String json) {
        Pattern pattern = Pattern.compile(String.format("\"%s\":\"(.*?)\"", key));
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String getLatestToolVersion() throws IOException, KeyManagementException,
            NoSuchAlgorithmException {
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        URL url = new URL(PRODUCTION_URL + "/versions/latest");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("user-agent", OSUtils.getUserAgent(getCurrentBallerinaVersion(),
                                                                   getCurrentToolsVersion(), "jballerina"));
        conn.setRequestProperty("Accept", "application/json");
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            throw new RuntimeException("Failed : HTTP error code : "
                                               + conn.getResponseCode());
        } else {
            String json = convertStreamToString(conn.getInputStream());
            Pattern p = Pattern.compile("\"version\":\"(.*?)\"");
            Matcher matcher = p.matcher(json);
            if (matcher.find()) {
                conn.disconnect();
                return matcher.group(1);
            }
        }
        conn.disconnect();
        throw new RuntimeException("Cannot find the latest tool version");
    }

    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            //TODO : do nothing
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                //TODO : do nothing
            }
        }
        return sb.toString();
    }

    /**
     * Provides path of the installed distributions.
     * @return installed distributions path
     */
    public static String getDistributionsPath() throws URISyntaxException {
        return OSUtils.getInstalltionPath() + File.separator + "distributions";
    }

    /**
     * Provides path of the installed dependencies.
     *
     * @return installed dependencies path
     */
    private static String getLibPath() throws URISyntaxException {
        return OSUtils.getInstalltionPath() + File.separator + "lib";
    }

    /**
     * Provides path of the tool unzip location.
     *
     * @return temporary directory to unzip update tool zip
     */
    private static String getToolUnzipLocation() throws URISyntaxException {
        return OSUtils.getInstalltionPath() + File.separator + "ballerina-command-tmp";
    }

    /**
     * Checks for command avaiable for current version.
     * @param printStream stream which messages should be printed
     * @param args current commands arguments
     */
    public static void checkForUpdate(PrintStream printStream, String[] args) {
        try {
            //Update check will be done only for build command
            boolean isBuildCommand = Arrays.stream(args).anyMatch("build"::equals);
            boolean isHelpFlag = Arrays.stream(args).anyMatch(val -> val.equals("--help") || val.equals("-h"));

            if (isBuildCommand && !isHelpFlag) {
                String version = getCurrentBallerinaVersion();
                if (OSUtils.updateNotice(version)) {
                    String latestVersion = ToolUtil.getLatest(version, "patch");
                    if (!latestVersion.equals(version)) {
                        printStream.println();
                        printStream.println("A new Ballerina version is available : " + latestVersion);
                        printStream.println("You can download the installer of it from " +
                                            "https://ballerina.io/downloads/.");
                        printStream.println();
                    }
                }
            }
        } catch (Exception e) {
            // If any exception occurs we are not letting users know as check for command is optional
        }
    }

    public static void downloadDistribution(PrintStream printStream, String distribution, boolean manualUpdate) {
        try {
            if (!ToolUtil.use(printStream, distribution)) {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

                String distributionType = distribution.split("-")[0];
                String distributionVersion = distribution.replace(distributionType + "-", "");
                URL url = new URL(ToolUtil.PRODUCTION_URL + "/distributions/" + distributionVersion);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("user-agent",
                        OSUtils.getUserAgent(distributionVersion, ToolUtil.getCurrentToolsVersion(), distributionType));
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() == 302) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("content-type", "binary/data");
                    ToolUtil.downloadAndSetupDist(printStream, conn, distribution, manualUpdate);
                } else if (conn.getResponseCode() == 200) {
                    ToolUtil.downloadAndSetupDist(printStream, conn, distribution, manualUpdate);
                } else {
                    printStream.println(distribution + " is not found ");
                }
            }
        } catch (IOException | KeyManagementException | NoSuchAlgorithmException e) {
            printStream.println("Cannot connect to the central server");
        } catch (URISyntaxException e) {
            printStream.println("Ballerina installation directory is not available");
        }
    }

    private static void downloadAndSetupDist(PrintStream printStream, HttpURLConnection conn,
                                             String distribution, boolean manual)
                                                throws IOException, URISyntaxException {
        String distPath = getDistributionsPath();
        if (new File(distPath).canWrite()) {
            String zipFileLocation = getDistributionsPath() + File.separator + distribution + ".zip";
            downloadFile(conn, zipFileLocation, distribution);
            printStream.println();
            unzip(zipFileLocation, distPath);
            addExecutablePermissionToFile(new File(distPath + File.separator + distribution
                                                           + File.separator + "bin"
                                                           + File.separator + OSUtils.getExecutableFileName()));
            new File(zipFileLocation).delete();
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }
            conn.disconnect();
            if (manual) {
                printStream.println(distribution + " is installed. Please execute \"ballerina dist use " +
                                            "" + distribution + "\" to use as the default");
            }
        } else {
            printStream.println("Current user does not have write permissions to " + distPath + " directory");
        }
    }

    public static void downloadTool(PrintStream printStream, String toolVersion) {
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            URL url = new URL(ToolUtil.PRODUCTION_URL + "/versions/" + toolVersion);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("user-agent", OSUtils.getUserAgent(getCurrentBallerinaVersion(),
                                                                       getCurrentToolsVersion(), "jballerina"));
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() == 302) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("content-type", "binary/data");
                downloadAndSetupTool(printStream, conn, "ballerina-command-" + toolVersion);
            } else if (conn.getResponseCode() == 200) {
                downloadAndSetupTool(printStream, conn, "ballerina-command-" + toolVersion);
            } else {
                printStream.println(toolVersion + " is not found ");
            }

        } catch (IOException | KeyManagementException | NoSuchAlgorithmException | URISyntaxException e) {
            printStream.println("Cannot connect to the central server due to" + e.getMessage());
        }
    }

    private static void downloadAndSetupTool(PrintStream printStream, HttpURLConnection conn,
                                             String toolFileName) throws IOException, URISyntaxException {
        String libsPath = getLibPath();
        if (new File(libsPath).canWrite()) {
            printStream.println("Downloading " + toolFileName);
            String toolUnzipLocation = getToolUnzipLocation();
            File tempUnzipDirectory = Paths.get(toolUnzipLocation).toFile();
            if (tempUnzipDirectory.exists()) {
                tempUnzipDirectory.delete();
            }
            tempUnzipDirectory.mkdir();
            String zipFileLocation = toolUnzipLocation + File.separator + toolFileName + ".zip";
            downloadFile(conn, zipFileLocation, toolFileName);
            printStream.println();
            unzip(zipFileLocation, toolUnzipLocation);
            copyScripts(toolUnzipLocation, toolFileName);
            new File(toolUnzipLocation).delete();
            new File(zipFileLocation).delete();
            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }
            conn.disconnect();
        } else {
            printStream.println("Current user does not have write permissions to directory");
        }
    }

    private static void downloadFile(HttpURLConnection conn, String zipFileLocation, String fileName)
            throws IOException {
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(zipFileLocation)) {
            byte[] b = new byte[1024];
            int count;
            int progress = 0;
            long totalSizeInMB = conn.getContentLengthLong() / (1024 * 1024);

            try (ProgressBar progressBar = new ProgressBar("Downloading " + fileName,
                    totalSizeInMB, ProgressBarStyle.ASCII)) {
                while ((count = in.read(b)) > 0) {
                    out.write(b, 0, count);
                    progress++;
                    if (progress % 1024 == 0) {
                        progressBar.step();
                    }
                }
            }
        }
    }

    private static void unzip(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry();
            while (entry != null) {
                String filePath = destDirectory + File.separator + entry.getName();
                if (!entry.isDirectory()) {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
                    byte[] bytesIn = new byte[1024];
                    int read = 0;
                    while ((read = zipIn.read(bytesIn)) != -1) {
                        bos.write(bytesIn, 0, read);
                    }
                    bos.close();
                } else {
                    File dir = new File(filePath);
                    dir.mkdir();
                }
                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
        }
    }

    private static void copyScripts(String unzippedUpdateToolPath, String ballerinaCommandDir) throws IOException {
        String installScriptFileName = OSUtils.getInstallScriptFileName();
        Path installScript = Paths.get(unzippedUpdateToolPath, installScriptFileName);
        Files.copy(Paths.get(unzippedUpdateToolPath, ballerinaCommandDir, "scripts", installScriptFileName),
                   installScript);
        addExecutablePermissionToFile(installScript.toFile());
    }

    private static void addExecutablePermissionToFile(File file) throws IOException {
        file.setReadable(true, false);
        file.setExecutable(true, false);
        file.setWritable(true, false);
    }

    public static String readFileAsString(String path) throws IOException {
        InputStream is = ClassLoader.getSystemResourceAsStream(path);
        InputStreamReader inputStreamREader = null;
        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            inputStreamREader = new InputStreamReader(is, StandardCharsets.UTF_8);
            br = new BufferedReader(inputStreamREader);
            String content = br.readLine();
            if (content == null) {
                return sb.toString();
            }

            sb.append(content);

            while ((content = br.readLine()) != null) {
                sb.append('\n').append(content);
            }
        } finally {
            if (inputStreamREader != null) {
                try {
                    inputStreamREader.close();
                } catch (IOException ignore) {
                }
            }
            if (br != null) {
                try {
                    br.close();
                } catch (IOException ignore) {
                }
            }
        }
        return sb.toString();
    }
}


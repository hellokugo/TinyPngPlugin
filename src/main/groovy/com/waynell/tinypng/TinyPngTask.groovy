package com.waynell.tinypng

import com.tinify.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import java.lang.Exception
import java.security.MessageDigest
import java.text.DecimalFormat

/**
 * TingPng Task
 * @author Wayne
 */
public class TinyPngTask extends DefaultTask {

    def android
    static TinyPngExtension configuration

    static lastValidateKeyIndex = 0

    TinyPngTask() {
        description = 'Tiny Resources'
        group = 'tinypng'
        outputs.upToDateWhen { false }
        android = project.extensions.android
        configuration = project.tinyInfo
    }

    public static String formetFileSize(long fileS) {
        def df = new DecimalFormat("#.00")
        if (fileS == 0L) {
            return "0B"
        }

        if (fileS < 1024) {
            return df.format((double) fileS) + "B"
        } else if (fileS < 1048576) {
            return df.format((double) fileS / 1024) + "KB"
        } else if (fileS < 1073741824) {
            return df.format((double) fileS / 1048576) + "MB"
        } else {
            return df.format((double) fileS / 1073741824) + "GB"
        }
    }

    /**
     * 是否超过限制的大小进行压缩
     * @param fileS
     * @return
     */
    public static boolean isOverSize(long fileS) {
        //先转换kb，再比较
        if(!(configuration.overSize ?: false)
                || ((double)fileS / 1024) >= Double.valueOf(configuration.overSize)) {
            return true
        }

        return false
    }

    public static String generateMD5(File file) {
        MessageDigest digest = MessageDigest.getInstance("MD5")
        file.withInputStream(){ is ->
            int read
            byte[] buffer = new byte[8192]
            while((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] md5sum = digest.digest()
        BigInteger bigInt = new BigInteger(1, md5sum)
        return bigInt.toString(16).padLeft(32, '0')
    }

    public static TinyPngResult compress(File resDir, Iterable<String> whiteList, Iterable<TinyPngInfo> compressedList) {
        def newCompressedList = new ArrayList<TinyPngInfo>()
        def accountError = false
        def beforeTotalSize = 0
        def afterTotalSize = 0
        def apiKeys = configuration.apiKey
        label: for (File file : resDir.listFiles()) {
            def filePath = file.path
            def fileName = file.name

            for (String s : whiteList) {
                if (fileName ==~/$s/) {
                    println("match whit list, skip it >>>>>>>>>>>>> $filePath")
                    continue label
                }
            }

            for (TinyPngInfo info : compressedList) {
                if (filePath == info.path && generateMD5(file) == info.md5) {
                    continue label
                }
            }

            if (fileName.endsWith(".jpg") || fileName.endsWith(".png")) {
                if (fileName.contains(".9")) {
                    continue
                }

                println("find target pic >>>>>>>>>>>>> $filePath")

                def fis = new FileInputStream(file)

                if (!isOverSize(fis.available())) {
                    continue label
                }

                labelTinifyAccount:
                try {
                    def beforeSize = fis.available()
                    def beforeSizeStr = formetFileSize(beforeSize)

                    // Use the Tinify API client
                    def tSource = Tinify.fromFile("${resDir}/${fileName}")
                    tSource.toFile("${resDir}/${fileName}")

                    def afterSize = fis.available()
                    def afterSizeStr = formetFileSize(afterSize)

                    beforeTotalSize += beforeSize
                    afterTotalSize += afterSize
                    newCompressedList.add(new TinyPngInfo(filePath, beforeSizeStr, afterSizeStr, generateMD5(file)))

                    println("beforeSize: $beforeSizeStr -> afterSize: ${afterSizeStr}")
                } catch (AccountException e) {
                    if (lastValidateKeyIndex < apiKeys.size() - 1) {
                        println("API key " + apiKeys[lastValidateKeyIndex] +
                                " limit, change Key to compress.")
                        Tinify.setKey(apiKeys[++lastValidateKeyIndex])
                        println("current API key: " + Tinify.key())
                        continue labelTinifyAccount
                    }

                    println("AccountException: ${e.getMessage()}")
                    accountError = true
                    break
                    // Verify your API key and account limit.
                } catch (ClientException e) {
                    // Check your source image and request options.
                    println("ClientException: ${e.getMessage()}")
                } catch (ServerException e) {
                    // Temporary issue with the Tinify API.
                    println("ServerException: ${e.getMessage()}")
                } catch (ConnectionException e) {
                    // A network connection error occurred.
                    println("ConnectionException: ${e.getMessage()}")
                } catch (IOException e) {
                    // Something else went wrong, unrelated to the Tinify API.
                    println("IOException: ${e.getMessage()}")
                } catch (Exception e) {
                    println("Exception: ${e.toString()}")
                }
            }
        }
        return new TinyPngResult(beforeTotalSize, afterTotalSize, accountError, newCompressedList)
    }

    @TaskAction
    def run() {
        println(configuration.toString())

        if (!(configuration.resourceDir ?: false)) {
            println("Not found resources list")
            return
        }
        if (!(configuration.apiKey ?: false)) {
            println("Tiny API Key not set")
            return
        }

        def apiKey = configuration.apiKey

        lableTraversal:
        try {
            Tinify.setKey(apiKey.get(lastValidateKeyIndex))
            Tinify.validate()
            println("current API key: " + Tinify.key() + "go compress...")
        } catch (Exception e) {
            if (lastValidateKeyIndex < apiKey.size() - 1) {
                println("API key " + apiKeys[lastValidateKeyIndex] +
                        " limit, change Key to compress.")
                lastValidateKeyIndex++
                continue lableTraversal
            }

            println("Tiny Validation of API key failed.")
            ignored.printStackTrace()
            return
        }

        def compressedList = new ArrayList<TinyPngInfo>()
        def compressedListFile = new File("${project.projectDir}/compressed-resource.json")
        if (!compressedListFile.exists()) {
            compressedListFile.createNewFile()
        }
        else {
            try {
                def list = new JsonSlurper().parse(compressedListFile, "utf-8")
                if(list instanceof ArrayList) {
                    compressedList = list
                }
                else {
                    println("compressed-resource.json is invalid, ignore")
                }
            } catch (Exception ignored) {
                println("compressed-resource.json is invalid, ignore")
            }
        }

        def beforeSize = 0L
        def afterSize = 0L
        def error = false
        def newCompressedList = new ArrayList<TinyPngInfo>()
        configuration.resourceDir.each { d ->
            def dir = new File(d)
            if(dir.exists() && dir.isDirectory()) {
                if (!(configuration.resourcePattern ?: false)) {
                    configuration.resourcePattern = ["drawable[a-z-]*"]
                }
                configuration.resourcePattern.each { p ->
                    dir.eachDirMatch(~/$p/) { drawDir ->
                        if(!error) {
                            TinyPngResult result = compress(drawDir, configuration.whiteList, compressedList)
                            beforeSize += result.beforeSize
                            afterSize += result.afterSize
                            error = result.error
                            if (result.getResults()) {
                                newCompressedList.addAll(result.getResults())
                            }
                        }
                    }
                }
            }
        }

        if(newCompressedList) {
            for (TinyPngInfo newTinyPng : newCompressedList) {
                def index = compressedList.path.indexOf(newTinyPng.path)
                if (index >= 0) {
                    compressedList[index] = newTinyPng
                } else {
                    compressedList.add(0, newTinyPng)
                }
            }
            def jsonOutput = new JsonOutput()
            def json = jsonOutput.toJson(compressedList)
            compressedListFile.write(jsonOutput.prettyPrint(json), "utf-8")
            println("Task finish, compress ${newCompressedList.size()} files, before total size: ${formetFileSize(beforeSize)} after total size: ${formetFileSize(afterSize)}")
        }
    }
}
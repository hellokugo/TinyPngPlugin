package com.waynell.tinypng

/**
 * Create On 16/12/2016
 * @author Wayne
 */
public class TinyPngExtension {
    //只有超过oversize大小的图片才去做压缩，单位是k
    String overSize;
    ArrayList<String> apiKey;
    ArrayList<String> whiteList;
    ArrayList<String> resourceDir;
    ArrayList<String> resourcePattern;

    public TinyPngExtension() {
        overSize = ""
        apiKey = []
        whiteList = []
        resourceDir = []
        resourcePattern = []
    }

    @Override
    public String toString() {
        return "TinyPngExtension{" +
                "" + overSize + '\'' +
                "apiKey='" + apiKey + '\'' +
                ", whiteList=" + whiteList +
                ", resourceDir=" + resourceDir +
                ", resourcePattern=" + resourcePattern +
                '}';
    }
}

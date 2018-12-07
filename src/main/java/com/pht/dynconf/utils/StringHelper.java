package com.pht.dynconf.utils;

public class StringHelper {

    public static String replaceDot(String s,String replacement){
        if (s==null){
            return null;
        }

        return s.replace(".", replacement);
    }

}

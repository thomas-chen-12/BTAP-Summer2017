package com.gatechvip.btap.summer17clean;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

/**
 * Created by Thomas on 5/18/17.
 */

public class DropboxClient {

    public static DbxClientV2 getClient(String accessToken) {
        // Create Dropbox client

        // TODO: what is client identifier (first argument in the constructor)?
        DbxRequestConfig config = new DbxRequestConfig("dropbox/sample-app", "en_US");
        return new DbxClientV2(config, accessToken);
    }
}

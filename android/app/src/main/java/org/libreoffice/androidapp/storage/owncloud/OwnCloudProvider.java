package org.libreoffice.androidapp.storage.owncloud;

import java.io.File;
import java.net.URI;

import org.libreoffice.androidapp.R;
import org.libreoffice.androidapp.storage.DocumentProviderSettingsActivity;
import org.libreoffice.androidapp.storage.IDocumentProvider;
import org.libreoffice.androidapp.storage.IFile;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.preference.PreferenceManager;

import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.OwnCloudClientFactory;
import com.owncloud.android.lib.common.OwnCloudCredentialsFactory;
import com.owncloud.android.lib.common.operations.RemoteOperationResult;
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode;
import com.owncloud.android.lib.resources.files.FileUtils;
import com.owncloud.android.lib.resources.files.ReadFileRemoteOperation;
import com.owncloud.android.lib.resources.files.model.RemoteFile;


/**
 * Implementation of IDocumentProvider for ownCloud servers.
 */
public class OwnCloudProvider implements IDocumentProvider,
        OnSharedPreferenceChangeListener {

    private int id;

    private Context context;
    private OwnCloudClient client;
    private File cacheDir;

    private String serverUrl;
    private String userName;
    private String password;
    private RemoteOperationResult result;

    public OwnCloudProvider(int id, Context context) {
        this.id = id;
        this.context = context;

        // read preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        serverUrl = preferences.getString(
                DocumentProviderSettingsActivity.KEY_PREF_OWNCLOUD_SERVER, "");
        userName = preferences.getString(
                DocumentProviderSettingsActivity.KEY_PREF_OWNCLOUD_USER_NAME, "");
        password = preferences.getString(
                DocumentProviderSettingsActivity.KEY_PREF_OWNCLOUD_PASSWORD, "");

        setupClient();

        // make sure cache directory exists, and clear it
        // TODO: probably we should do smarter cache management
        cacheDir = new File(context.getCacheDir(), "ownCloud");
        if (cacheDir.exists()) {
            deleteRecursive(cacheDir);
        }
        cacheDir.mkdirs();
    }

    private void setupClient() {
        Uri serverUri = Uri.parse(serverUrl);
        client = OwnCloudClientFactory.createOwnCloudClient(serverUri, context,
                true);
        client.setCredentials(OwnCloudCredentialsFactory.newBasicCredentials(
                userName, password));
    }

    @Override
    public IFile getRootDirectory(Context context) {
        return createFromUri(context, URI.create(FileUtils.PATH_SEPARATOR));
    }

    @Override
    public IFile createFromUri(Context context, URI uri) {
        if(serverUrl != "" || userName != "" || password != ""){
            ReadFileRemoteOperation refreshOperation = new ReadFileRemoteOperation(
                    uri.getPath());
            this.result = refreshOperation.execute(client);
            if (!result.isSuccess()) {
                throw buildRuntimeExceptionForResultCode(result.getCode());
            }
            if (result.getData().size() > 0) {
                return new OwnCloudFile(this, (RemoteFile) result.getData().get(0));
            }
        } else {
            throw buildRuntimeExceptionForResultCode(ResultCode.WRONG_CONNECTION);
        }

        return null;
    }

    @Override
    public int getNameResource() {
        return R.string.remote_server;
    }

    /**
     * Used by OwnCloudFiles to get a configured client to run their own
     * operations.
     *
     * @return configured OwnCloudClient.
     */
    protected OwnCloudClient getClient() {
        return client;
    }

    /**
     * Used by OwnCloudFiles to get the cache directory they should download
     * files to.
     *
     * @return cache directory.
     */
    protected File getCacheDir() {
        return cacheDir;
    }

    /**
     * Build the proper RuntimeException for some error result.
     *
     * @param code Result code got from some RemoteOperationResult.
     * @return exception with the proper internationalized error message.
     */
    protected RuntimeException buildRuntimeExceptionForResultCode(ResultCode code) {
        int errorMessage;
        switch (code) {
            case WRONG_CONNECTION:  // SocketException
            case FILE_NOT_FOUND:    // HTTP 404
                errorMessage = R.string.owncloud_wrong_connection;
                break;
            case UNAUTHORIZED:      // wrong user/pass
                errorMessage = R.string.owncloud_unauthorized;
                break;
            default:
                errorMessage = R.string.owncloud_unspecified_error;
                break;
        }
        return new RuntimeException(context.getString(errorMessage));
    }

    /**
     * Deletes files and recursively deletes directories.
     *
     * @param file
     *            File or directory to be deleted.
     */
    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles())
                deleteRecursive(child);
        }
        file.delete();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences,
            String key) {
        boolean changed = false;
        if (key.equals(DocumentProviderSettingsActivity.KEY_PREF_OWNCLOUD_SERVER)) {
            serverUrl = preferences.getString(key, "");
            changed = true;
        }
        else if (key.equals(DocumentProviderSettingsActivity.KEY_PREF_OWNCLOUD_USER_NAME)) {
            userName = preferences.getString(key, "");
            changed = true;
        }
        else if (key.equals(DocumentProviderSettingsActivity.KEY_PREF_OWNCLOUD_PASSWORD)) {
            password = preferences.getString(key, "");
            changed = true;
        }

        if (changed)
            setupClient();
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public boolean checkProviderAvailability(Context context) {
        return client != null;
    }
}

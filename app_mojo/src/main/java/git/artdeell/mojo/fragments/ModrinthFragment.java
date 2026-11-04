package git.artdeell.mojo.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModrinthFragment extends Fragment {

    private EditText searchBox;
    private LinearLayout resultsView;
    private String category = "mod";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String[]> filePicker;

    public ModrinthFragment() {
        super(R.layout.fragment_modrinth);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) importFile(uri);
                }
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        searchBox = view.findViewById(R.id.mod_search);
        resultsView = view.findViewById(R.id.mod_results);

        Button mods = view.findViewById(R.id.tab_mods);
        Button shaders = view.findViewById(R.id.tab_shaders);
        Button packs = view.findViewById(R.id.tab_packs);
        Button importButton =
                view.findViewById(R.id.import_content_button);

        mods.setOnClickListener(v -> {
            category = "mod";
            search();
        });

        shaders.setOnClickListener(v -> {
            category = "shader";
            search();
        });

        packs.setOnClickListener(v -> {
            category = "resourcepack";
            search();
        });

        importButton.setOnClickListener(v ->
                filePicker.launch(new String[]{"*/*"})
        );

        searchBox.setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH
                            || actionId == EditorInfo.IME_ACTION_DONE) {
                        search();
                        return true;
                    }
                    return false;
                }
        );

        search();
    }

    private void onUi(Runnable action) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (isAdded()) action.run();
        });
    }

    private void showMessage(String message) {
        onUi(() -> {
            resultsView.removeAllViews();

            TextView text = new TextView(requireContext());
            text.setText(message);
            text.setTextColor(0xFFFFFFFF);
            text.setTextSize(15);
            text.setPadding(16, 24, 16, 24);

            resultsView.addView(text);
        });
    }

    private void search() {
        String query = searchBox.getText().toString().trim();
        String selectedCategory = category;

        showMessage("Searching Modrinth...");

        executor.execute(() -> {
            try {
                String facets = URLEncoder.encode(
                        "[[\"project_type:" + selectedCategory + "\"]]",
                        "UTF-8"
                );

                String url =
                        "https://api.modrinth.com/v2/search?query="
                        + URLEncoder.encode(query, "UTF-8")
                        + "&facets=" + facets
                        + "&limit=20";

                JSONObject response = new JSONObject(request(url));
                JSONArray hits = response.getJSONArray("hits");

                showProjects(hits, selectedCategory);

            } catch (Exception e) {
                showMessage("Could not load Modrinth results. Check your internet connection.");
            }
        });
    }

    private void showProjects(
            JSONArray projects,
            String selectedCategory) {

        onUi(() -> {
            resultsView.removeAllViews();

            if (projects.length() == 0) {
                showMessage("No results found.");
                return;
            }

            for (int i = 0; i < projects.length(); i++) {
                JSONObject project = projects.optJSONObject(i);
                if (project == null) continue;

                String title = project.optString("title");
                String description =
                        project.optString("description");
                String downloads =
                        String.valueOf(project.optLong("downloads"));

                TextView item = new TextView(requireContext());

                item.setText(
                        (i + 1) + ". " + title
                        + "\n" + description
                        + "\nDownloads: " + downloads
                        + "\nTap to view compatible versions"
                );

                item.setTextColor(0xFFFFFFFF);
                item.setTextSize(14);
                item.setPadding(16, 18, 16, 18);

                item.setOnClickListener(v ->
                        chooseLoader(project, selectedCategory)
                );

                resultsView.addView(item);

                View divider = new View(requireContext());
                divider.setBackgroundColor(0xFF444444);

                LinearLayout.LayoutParams params =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1
                        );

                resultsView.addView(divider, params);
            }
        });
    }

    private void chooseLoader(
            JSONObject project,
            String selectedCategory) {

        String[] loaders;
        String message;

        if ("mod".equals(selectedCategory)) {
            loaders = new String[]{
                    "Fabric", "Forge", "NeoForge", "Quilt"
            };
            message = "Select your mod loader";
        } else if ("shader".equals(selectedCategory)) {
            loaders = new String[]{"Iris", "OptiFine"};
            message = "Select your shader loader";
        } else {
            loaders = new String[]{"Minecraft"};
            message = "Select resource pack type";
        }

        onUi(() -> new AlertDialog.Builder(requireContext())
                .setTitle(message)
                .setItems(loaders, (dialog, which) -> {
                    String loader = loaders[which]
                            .toLowerCase();

                    loadMinecraftVersion(
                            project,
                            selectedCategory,
                            loader
                    );
                })
                .setNegativeButton("Cancel", null)
                .show());
    }

    private void loadMinecraftVersion(
            JSONObject project,
            String selectedCategory,
            String loader) {

        executor.execute(() -> {
            try {
                Instance instance =
                        Instances.loadSelectedInstance();

                if (instance == null) {
                    showMessage("No Minecraft instance selected.");
                    return;
                }

                String mcVersion = instance.versionId;

                if (mcVersion == null
                        || mcVersion.isEmpty()
                        || mcVersion.equals("latest_release")
                        || mcVersion.equals("latest_snapshot")) {

                    onUi(() -> {
                        EditText input =
                                new EditText(requireContext());

                        input.setSingleLine(true);
                        input.setInputType(
                                InputType.TYPE_CLASS_TEXT
                        );
                        input.setHint("Example: 1.20.1");

                        new AlertDialog.Builder(requireContext())
                                .setTitle("Minecraft version")
                                .setMessage(
                                        "Enter the Minecraft version for this instance."
                                )
                                .setView(input)
                                .setPositiveButton("Continue",
                                        (dialog, which) -> {
                                            String version = input
                                                    .getText()
                                                    .toString()
                                                    .trim();

                                            if (!version.isEmpty()) {
                                                fetchVersions(
                                                        project,
                                                        selectedCategory,
                                                        loader,
                                                        version
                                                );
                                            }
                                        })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });

                } else {
                    fetchVersions(
                            project,
                            selectedCategory,
                            loader,
                            mcVersion
                    );
                }

            } catch (Exception e) {
                showMessage("Could not read selected Minecraft instance.");
            }
        });
    }

    private void fetchVersions(
            JSONObject project,
            String selectedCategory,
            String loader,
            String mcVersion) {

        showMessage("Finding compatible versions...");

        executor.execute(() -> {
            try {
                String loaders = URLEncoder.encode(
                        new JSONArray().put(loader).toString(),
                        "UTF-8"
                );

                String gameVersions = URLEncoder.encode(
                        new JSONArray().put(mcVersion).toString(),
                        "UTF-8"
                );

                String projectId =
                        project.getString("project_id");

                String url =
                        "https://api.modrinth.com/v2/project/"
                        + URLEncoder.encode(projectId, "UTF-8")
                        + "/version?loaders=" + loaders
                        + "&game_versions=" + gameVersions;

                JSONArray versions = new JSONArray(request(url));

                if (versions.length() == 0) {
                    showMessage(
                            "No compatible versions found for Minecraft "
                            + mcVersion + " and " + loader + "."
                    );
                    return;
                }

                showVersions(
                        project,
                        versions,
                        selectedCategory,
                        mcVersion
                );

            } catch (Exception e) {
                showMessage("Could not load compatible versions.");
            }
        });
    }

    private void showVersions(
            JSONObject project,
            JSONArray versions,
            String selectedCategory,
            String mcVersion) {

        onUi(() -> {
            String[] labels = new String[versions.length()];

            for (int i = 0; i < versions.length(); i++) {
                JSONObject version = versions.optJSONObject(i);

                if (version == null) {
                    labels[i] = "Unknown version";
                    continue;
                }

                labels[i] =
                        version.optString("version_number")
                        + " - "
                        + version.optString("version_type")
                        + " - "
                        + version.optString("name");
            }

            new AlertDialog.Builder(requireContext())
                    .setTitle(
                            project.optString("title")
                            + "\nMinecraft " + mcVersion
                    )
                    .setItems(labels, (dialog, which) -> {
                        JSONObject selected =
                                versions.optJSONObject(which);

                        if (selected != null) {
                            confirmInstall(
                                    selected,
                                    selectedCategory,
                                    project.optString("title")
                            );
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void confirmInstall(
            JSONObject version,
            String selectedCategory,
            String projectTitle) {

        JSONArray dependencies =
                version.optJSONArray("dependencies");

        int required = 0;

        if (dependencies != null) {
            for (int i = 0; i < dependencies.length(); i++) {
                JSONObject dependency =
                        dependencies.optJSONObject(i);

                if (dependency != null
                        && "required".equals(
                                dependency.optString("dependency_type"))) {
                    required++;
                }
            }
        }

        String message = "Install " + projectTitle + "?";

        if (required > 0) {
            message += "\n\nThis version has " + required
                    + " required dependencies. They are not installed automatically yet.";
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Confirm installation")
                .setMessage(message)
                .setPositiveButton("Install",
                        (dialog, which) ->
                                installVersion(
                                        version,
                                        selectedCategory
                                ))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void installVersion(
            JSONObject version,
            String selectedCategory) {

        showMessage("Downloading and installing...");

        executor.execute(() -> {
            File temporaryFile = null;

            try {
                Instance instance =
                        Instances.loadSelectedInstance();

                if (instance == null) {
                    throw new Exception(
                            "No Minecraft instance selected."
                    );
                }

                JSONArray files = version.getJSONArray("files");

                if (files.length() == 0) {
                    throw new Exception("No download file available.");
                }

                JSONObject selectedFile = files.getJSONObject(0);

                for (int i = 0; i < files.length(); i++) {
                    JSONObject candidate = files.getJSONObject(i);

                    if (candidate.optBoolean("primary")) {
                        selectedFile = candidate;
                        break;
                    }
                }

                String fileName =
                        selectedFile.getString("filename");

                String downloadUrl =
                        selectedFile.getString("url");

                String folder;
                String extension;

                if ("mod".equals(selectedCategory)) {
                    folder = "mods";
                    extension = ".jar";
                } else if ("shader".equals(selectedCategory)) {
                    folder = "shaderpacks";
                    extension = ".zip";
                } else {
                    folder = "resourcepacks";
                    extension = ".zip";
                }

                if (!fileName.toLowerCase().endsWith(extension)) {
                    throw new Exception(
                            "Unexpected file type: " + fileName
                    );
                }

                File destinationFolder = new File(
                        instance.getGameDirectory(),
                        folder
                );

                if (!destinationFolder.exists()
                        && !destinationFolder.mkdirs()) {
                    throw new Exception(
                            "Could not create " + folder + " folder."
                    );
                }

                fileName = new File(fileName).getName()
                        .replaceAll("[^a-zA-Z0-9._+-]", "_");

                File destination =
                        new File(destinationFolder, fileName);

                if (destination.exists()) {
                    throw new Exception(
                            "This file is already installed."
                    );
                }

                temporaryFile =
                        new File(destinationFolder, fileName + ".part");

                downloadFile(downloadUrl, temporaryFile);

                if (!temporaryFile.renameTo(destination)) {
                    throw new Exception(
                            "Could not save downloaded file."
                    );
                }

                temporaryFile = null;

                showToast("Successfully installed: " + fileName);

            } catch (Exception e) {
                if (temporaryFile != null) {
                    temporaryFile.delete();
                }

                showToast("Installation failed: " + e.getMessage());
            }
        });
    }

    private void downloadFile(String url, File destination)
            throws Exception {

        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)
                    new URL(url).openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "User-Agent",
                    "JarLauncher/1.0 (Android)"
            );

            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception(
                        "Download server error: " + responseCode
                );
            }

            try (
                    InputStream input = connection.getInputStream();
                    FileOutputStream output =
                            new FileOutputStream(destination)
            ) {
                byte[] buffer = new byte[8192];
                int count;

                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
            }

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

 

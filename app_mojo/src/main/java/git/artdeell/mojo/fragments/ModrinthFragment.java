
package git.artdeell.mojo.fragments;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
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
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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
                    if (uri != null) {
                        importFile(uri);
                    }
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

    private void showMessage(String message) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;

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

        showMessage("Searching Modrinth...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encodedQuery =
                        URLEncoder.encode(query, "UTF-8");

                String facets = URLEncoder.encode(
                        "[[\"project_type:" + category + "\"]]",
                        "UTF-8"
                );

                URL url = new URL(
                        "https://api.modrinth.com/v2/search?query="
                                + encodedQuery
                                + "&facets=" + facets
                                + "&limit=20"
                );

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty(
                        "User-Agent",
                        "JarLauncher/1.0 (Android)"
                );

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream()
                        )
                );

                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                JSONObject json =
                        new JSONObject(response.toString());

                JSONArray hits = json.getJSONArray("hits");
                StringBuilder output = new StringBuilder();

                if (hits.length() == 0) {
                    output.append("No results found.");
                }

                for (int i = 0; i < hits.length(); i++) {
                    JSONObject project = hits.getJSONObject(i);

                    output.append("\n")
                            .append(i + 1)
                            .append(". ")
                            .append(project.optString("title"))
                            .append("\n");

                    output.append(project.optString(
                            "description", "No description"
                    )).append("\n");

                    output.append("Downloads: ")
                            .append(project.optLong("downloads"))
                            .append("\n\n");
                }

                showMessage(output.toString());

            } catch (Exception e) {
                showMessage("Could not load Modrinth results. Check your internet connection.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private void importFile(Uri uri) {
        Context context =
                requireContext().getApplicationContext();

        String selectedCategory = category;

        executor.execute(() -> {
            try {
                String fileName = getFileName(context, uri);

                if (fileName == null || fileName.isEmpty()) {
                    throw new Exception("Could not read file name.");
                }

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
                            "Wrong file type. Expected " + extension
                    );
                }

                Instance instance =
                        Instances.loadSelectedInstance();

                if (instance == null) {
                    throw new Exception(
                            "No Minecraft instance selected."
                    );
                }

                File gameDirectory = instance.getGameDirectory();
                File destinationFolder =
                        new File(gameDirectory, folder);

                if (!destinationFolder.exists()
                        && !destinationFolder.mkdirs()) {
                    throw new Exception(
                            "Could not create " + folder + " folder."
                    );
                }

                File destination = new File(
                        destinationFolder,
                        new File(fileName).getName()
                );

                if (destination.exists()) {
                    throw new Exception(
                            "This file is already imported."
                    );
                }

                try (
                        InputStream input =
                                context.getContentResolver()
                                        .openInputStream(uri);
                        OutputStream output =
                                new FileOutputStream(destination)
                ) {
                    if (input == null) {
                        throw new Exception("Cannot open selected file.");
                    }

                    byte[] buffer = new byte[8192];
                    int count;

                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }

                showToast("Successfully imported: " + fileName);

            } catch (Exception e) {
                showToast("Import failed: " + e.getMessage());
            }
        });
    }

    private String getFileName(Context context, Uri uri) {
        String name = null;

        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(0);
            }
        } catch (Exception ignored) {
        }

        return name;
    }

    private void showToast(String message) {
        if (!isAdded()) return;

        requireActivity().runOnUiThread(() -> {
            if (isAdded()) {
                Toast.makeText(
                        requireContext(),
                        message,
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}

package git.artdeell.mojo.fragments;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ModrinthFragment extends Fragment {

    private EditText searchBox;
    private TextView resultsView;
    private String category = "mod";
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public ModrinthFragment() {
        super(R.layout.fragment_modrinth);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        searchBox = view.findViewById(R.id.mod_search);
        resultsView = view.findViewById(R.id.mod_results);

        Button mods = view.findViewById(R.id.tab_mods);
        Button shaders = view.findViewById(R.id.tab_shaders);
        Button packs = view.findViewById(R.id.tab_packs);

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

        searchBox.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                search();
                return true;
            }
            return false;
        });

        search();
    }

    private void search() {
        String query = searchBox.getText().toString().trim();
        resultsView.setText("Searching Modrinth...");

        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                String encodedQuery =
                        URLEncoder.encode(query, "UTF-8");

                String facets =
                        URLEncoder.encode(
                                "[[\"project_type:" + category + "\"]]",
                                "UTF-8");

                URL url = new URL(
                        "https://api.modrinth.com/v2/search?query="
                                + encodedQuery
                                + "&facets=" + facets
                                + "&limit=20"
                );

                connection = (HttpURLConnection) url.openConnection();
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

                if (isAdded()) {
                    requireActivity().runOnUiThread(
                            () -> resultsView.setText(output.toString())
                    );
                }

            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(
                            () -> resultsView.setText(
                                    "Could not load Modrinth results. Check your internet connection and try again."
                            )
                    );
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
              }

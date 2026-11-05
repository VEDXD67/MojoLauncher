
package git.artdeell.mojo.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SkinCapeFragment extends Fragment {

    private ImageView skinPreview;

    private ActivityResultLauncher<String[]> skinPicker;
    private ActivityResultLauncher<String[]> capePicker;

    private File skinFile;
    private File capeFile;

    public SkinCapeFragment() {
        super(R.layout.fragment_skin_cape);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        skinPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        saveImage(uri, "skin");
                    }
                }
        );

        capePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        saveImage(uri, "cape");
                    }
                }
        );
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState) {

        super.onViewCreated(view, savedInstanceState);

        skinPreview = view.findViewById(R.id.skin_preview);

        Button changeSkin =
                view.findViewById(R.id.change_skin_button);

        Button changeCape =
                view.findViewById(R.id.change_cape_button);

        Button apply =
                view.findViewById(R.id.apply_skin_button);

        skinFile = new File(
                requireContext().getFilesDir(),
                "custom_skin.png"
        );

        capeFile = new File(
                requireContext().getFilesDir(),
                "custom_cape.png"
        );

        if (skinFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(
                    skinFile.getAbsolutePath()
            );

            if (bitmap != null) {
                skinPreview.setImageBitmap(bitmap);
            }
        }

        changeSkin.setOnClickListener(v ->
                skinPicker.launch(new String[]{"image/png"})
        );

        changeCape.setOnClickListener(v ->
                capePicker.launch(new String[]{"image/png"})
        );

        apply.setOnClickListener(v -> {
            boolean hasSkin = skinFile.exists();
            boolean hasCape = capeFile.exists();

            if (!hasSkin && !hasCape) {
                Toast.makeText(
                        requireContext(),
                        "Choose a skin or cape first.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            Toast.makeText(
                    requireContext(),
                    "Skin and cape saved locally!",
                    Toast.LENGTH_LONG
            ).show();
        });
    }

    private void saveImage(Uri uri, String type) {
        try {
            File destination = type.equals("skin")
                    ? skinFile : capeFile;

            if (destination == null) {
                destination = new File(
                        requireContext().getFilesDir(),
                        type.equals("skin")
                                ? "custom_skin.png"
                                : "custom_cape.png"
                );
            }

            try (
                    InputStream input =
                            requireContext()
                                    .getContentResolver()
                                    .openInputStream(uri);

                    FileOutputStream output =
                            new FileOutputStream(destination)
            ) {
                if (input == null) {
                    throw new Exception("Could not open image");
                }

                byte[] buffer = new byte[8192];
                int length;

                while ((length = input.read(buffer)) != -1) {
                    output.write(buffer, 0, length);
                }
            }

            if (type.equals("skin")) {
                skinFile = destination;

                Bitmap bitmap = BitmapFactory.decodeFile(
                        destination.getAbsolutePath()
                );

                if (bitmap != null) {
                    skinPreview.setImageBitmap(bitmap);
                }
            } else {
                capeFile = destination;
            }

            Toast.makeText(
                    requireContext(),
                    type.equals("skin")
                            ? "Skin selected!"
                            : "Cape selected!",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {
            Toast.makeText(
                    requireContext(),
                    "Image selection failed: " + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }
                  }

package com.example.musicapp;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.splashscreen.SplashScreen;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.chibde.visualizer.BarVisualizer;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.jgabrielfreitas.core.BlurImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

import de.hdodenhof.circleimageview.CircleImageView;
import jp.wasabeef.recyclerview.adapters.ScaleInAnimationAdapter;

public class MainActivity extends AppCompatActivity {

    //Members
    RecyclerView recyclerView;
    SongAdapter songAdapter;
    List<Song> allSongs = new ArrayList<>();
    ExoPlayer player;

    ActivityResultLauncher<String> storagePermissionLauncher;
    final String permission = Manifest.permission.READ_EXTERNAL_STORAGE;
    ActivityResultLauncher<String> recordAudioPermissionLaucher; //to access te song adapter
    final String recordAudioPermission = Manifest.permission.RECORD_AUDIO;
    //controls
    TextView songNameView, skipPrevBtn, skipNextBtn, playPauseBtn, repeatModeBtn, playlistBtn;
    TextView homeSongNameView, homeSkipPrevBtn, homePlayPauseBtn, homeSkipNextBtn;
    //wrappers
    ConstraintLayout homeControlWrapper, headWrapper, artworkWrapper, seekbarWrapper, controlWrapper, audioVisualizerWrapper;
    ConstraintLayout playerView;
    TextView playerCloseBtn;
    //artwork
    CircleImageView artworkView;
    //seek bar
    SeekBar seekbar;
    TextView progressView, durationView;
    //audio visualizer
    BarVisualizer audioVisualizer;
    //blur image view
    BlurImageView blurImageView;
    //status bar & navigation color
    int defaultStatusColor;
    //repeat mode
    int repeatMode = 1; // repeat all = 1, repeat one = 2, shuffle all = 3
    //is the act. bound?
    boolean isBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //install splash screen
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //save the status color
        defaultStatusColor = getWindow().getStatusBarColor();
        //set the navigation color
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199)); //0 & 255

        //Set tool bar and app title
        Toolbar toolbar = findViewById(R.id.toolBar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle(getResources().getString(R.string.app_name));

        //RecycleView
        recyclerView = findViewById(R.id.recycleView);
        storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted->{
            if (granted){
                fetchSongs();
            }
            else {
                userRespones();
            }
        });

        //launch storage permission on create
        storagePermissionLauncher.launch(permission);

        //record audio permission
        recordAudioPermissionLaucher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted->{
           if (granted && player.isPlaying()){
               activateAudioVisualizer();
           }
           else {
               userResponesesOnRecordAudioPerm();
           }
        });

        //views
        //player = new ExoPlayer.Builder(this).build();
        playerView = findViewById(R.id.playerView);
        playerCloseBtn = findViewById(R.id.playerCloseBtn);
        songNameView = findViewById(R.id.songNameView);
        skipPrevBtn = findViewById(R.id.skipPreviousBtn);
        skipNextBtn = findViewById(R.id.skipNextBtn);
        playPauseBtn = findViewById(R.id.playPauseBtn);
        repeatModeBtn = findViewById(R.id.repeatModeBtn);
        playlistBtn = findViewById(R.id.playlistBtn);

        homeSongNameView = findViewById(R.id.homeSongNameView);
        homeSkipPrevBtn = findViewById(R.id.homeSkipPrevBtn);
        homeSkipNextBtn = findViewById(R.id.homeSkipNextBtn);
        homePlayPauseBtn = findViewById(R.id.homePlayPauseBtn);

        //wrappers
        homeControlWrapper = findViewById(R.id.homeControlWrapper);
        headWrapper = findViewById(R.id.headWrapper);
        artworkWrapper = findViewById(R.id.artworkWrapper);
        seekbarWrapper = findViewById(R.id.seekBarWrapper);
        controlWrapper = findViewById(R.id.controlWrapper);
        audioVisualizerWrapper = findViewById(R.id.audioVisualizerWrapper);

        //artwork
        artworkView = findViewById(R.id.artworkView);

        //seek bar
        seekbar = findViewById(R.id.seekBar);
        progressView = findViewById(R.id.progressView);
        durationView = findViewById(R.id.durationView);

        //audio visualizer
        audioVisualizer = findViewById(R.id.visualizer);

        //blur image view
        blurImageView = findViewById(R.id.blurImageView);

        //player control method
        //playerControls();

        //bind to the player service, and do everything after the binding
        doBindService();
    }

    private void doBindService() {
        Intent playerServiceIntent = new Intent(this, PlayerService.class);
        bindService(playerServiceIntent, playerServiceConnection, Context.BIND_AUTO_CREATE);
    }

    ServiceConnection playerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder iBinder) {
            //get the service instance
            PlayerService.ServiceBinder binder = (PlayerService.ServiceBinder) iBinder;
            player = binder.getPlayerService().player;
            isBound = true;
            //ready to show songs
            storagePermissionLauncher.launch(permission);
            //call player control method
            playerControls();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @SuppressLint("SuspiciousIndentation")
    @Override
    public void onBackPressed() {
        //we say if the player view is visible, close it
        if (playerView.getVisibility() == View.VISIBLE){
            exitPlayerView();
        }
        else
        super.onBackPressed();
    }

    private void playerControls() {
        //song name marquee
        songNameView.setSelected(true);
        homeSongNameView.setSelected(true);

        //exit the player view
        playerCloseBtn.setOnClickListener(v -> exitPlayerView());
        playlistBtn.setOnClickListener(v -> exitPlayerView());

        //open player view on ome control wrapper click
        homeControlWrapper.setOnClickListener(v -> showPlayerView());

        //player listener
        player.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                Player.Listener.super.onMediaItemTransition(mediaItem, reason);

                //show the song title
                assert mediaItem != null;
                songNameView.setText(mediaItem.mediaMetadata.title);
                homeSongNameView.setText(mediaItem.mediaMetadata.title);

                progressView.setText(getReadableTime((int) player.getCurrentPosition()));
                seekbar.setProgress((int) player.getCurrentPosition());
                seekbar.setMax((int) player.getDuration());
                durationView.setText(getReadableTime((int) player.getDuration()));
                playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_circle_outline, 0, 0, 0);
                homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);

                //show the current artwork
                showCurrentArtwork();

                //update the progress position of a current playing song
                updatePlayerPositionProgress();

                //load the artwork animation
                artworkView.setAnimation(loadRotation());

                //audio visualizer
                activateAudioVisualizer();

                //update player view colors
                updatePlayerColors();

                if (!player.isPlaying()){
                    player.play();
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                Player.Listener.super.onPlaybackStateChanged(playbackState);
                if (playbackState == ExoPlayer.STATE_READY){
                    //set values to player views
                    songNameView.setText(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.title);
                    homeSongNameView.setText(player.getCurrentMediaItem().mediaMetadata.title);
                    progressView.setText(getReadableTime((int) player.getCurrentPosition()));
                    durationView.setText((getReadableTime((int) player.getDuration())));
                    seekbar.setMax((int) player.getDuration());
                    seekbar.setProgress((int) player.getCurrentPosition());
                    playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_circle_outline, 0, 0, 0);
                    homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);

                    //show the current artwork
                    showCurrentArtwork();

                    //update the progress position of a current playing song
                    updatePlayerPositionProgress();

                    //load the artwork animation
                    artworkView.setAnimation(loadRotation());

                    //audio visualizer
                    activateAudioVisualizer();

                    //update player view colors
                    updatePlayerColors();
                }
                else {
                    playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_circle_outline, 0, 0, 0);
                    homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);

                }
            }
        });

        //skip to next track
        skipNextBtn.setOnClickListener(v -> skipToNextSong());
        homeSkipNextBtn.setOnClickListener(v -> skipToNextSong());

        //skip to prev track
        skipPrevBtn.setOnClickListener(v -> skipToPrevSong());
        homeSkipPrevBtn.setOnClickListener(v -> skipToPrevSong());

        //play or pause the player
        playPauseBtn.setOnClickListener(v -> playOrPausePlayer());
        homePlayPauseBtn.setOnClickListener(v -> playOrPausePlayer());

        //seek bar listener
        seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressValue = 0;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressValue = seekBar.getProgress();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(progressValue);
                progressView.setText(getReadableTime(progressValue));
                player.seekTo(progressValue);
            }
        });

        //repeat mode
        repeatModeBtn.setOnClickListener(v -> {
            if (repeatMode == 1){
                //repeat one
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE);
                repeatMode = 2;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat_on, 0, 0, 0);
            }
            else if (repeatMode == 2){
                //shuffle all
                player.setShuffleModeEnabled(true);
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
                repeatMode = 3;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_shuffle, 0, 0, 0);
            }
            else if (repeatMode == 3){
                //repeat all
                player.setRepeatMode(ExoPlayer.REPEAT_MODE_ALL);
                player.setShuffleModeEnabled(false);
                repeatMode = 1;
                repeatModeBtn.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_repeat, 0, 0, 0);

            }

            //update colors
            updatePlayerColors();
        });
    }

    private void playOrPausePlayer() {
        if (player.isPlaying()){
            player.pause();
            playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play_circle_outline, 0, 0, 0);
            homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0);
            artworkView.clearAnimation();
        }
        else {
            player.play();
            playPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_circle_outline, 0, 0, 0);
            homePlayPauseBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause, 0, 0, 0);
            artworkView.startAnimation(loadRotation());
        }

        //update player colors
        updatePlayerColors();
    }

    private void skipToPrevSong() {
        if (player.hasPreviousMediaItem()){
            player.seekToPrevious();
        }
    }

    private void skipToNextSong() {
        if (player.hasNextMediaItem()){
            player.seekToNext();
        }
    }

    private Animation loadRotation() {
        RotateAnimation rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setDuration(10000);
        rotateAnimation.setRepeatCount(Animation.INFINITE);

        return rotateAnimation;
    }

    private void updatePlayerPositionProgress() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (player.isPlaying()){
                    progressView.setText(getReadableTime((int) player.getCurrentPosition()));
                    seekbar.setProgress((int) player.getCurrentPosition());
                }

                //repeat calling the method
                updatePlayerPositionProgress();
            }
        }, 1000);
    }

    private void showCurrentArtwork() {
        artworkView.setImageURI(Objects.requireNonNull(player.getCurrentMediaItem()).mediaMetadata.artworkUri);

        if (artworkView.getDrawable() == null){
            artworkView.setImageResource(R.drawable.song_thumpnail);
        }

    }

    @SuppressLint("DefaultLocale")
    String getReadableTime(int duration) {
        String time;
        int hrs = duration/(1000*60*60);
        int min = (duration%(1000*60*60))/(1000*60);
        int sec = (((duration%(1000*60*60))%(1000*60*60))%(1000*60))/1000;

        if (hrs<1){
            time = String.format("%02d:%02d", min, sec);
        }
        else {
            time = String.format("%1d:%02d:%02d", hrs, min, sec);
        }
        return time;
    }


    private void showPlayerView() {
        playerView.setVisibility(View.VISIBLE);
        updatePlayerColors();
    }

    private void updatePlayerColors() {
        //only player view is visible
        if (playerView.getVisibility() == View.GONE){
            return;
        }

        BitmapDrawable bitmapDrawable = (BitmapDrawable) artworkView.getDrawable();
        if (bitmapDrawable == null){
            bitmapDrawable = (BitmapDrawable) ContextCompat.getDrawable(this, R.drawable.song_thumpnail);
        }

        assert bitmapDrawable != null;
        Bitmap bitmap = bitmapDrawable.getBitmap();

        //set bitmap to blur image view
        blurImageView.setImageBitmap(bitmap);
        blurImageView.setBlur(4);

        //player control colors
        Palette.from(bitmap).generate(palette -> {
            if (palette != null){
                Palette.Swatch swatch = palette.getDarkVibrantSwatch();
                if (swatch == null){
                    swatch = palette.getMutedSwatch();
                    if (swatch == null){
                        swatch = palette.getDominantSwatch();
                    }
                }

                //extract text color
                assert swatch != null;
                int titleTextColor = swatch.getTitleTextColor();
                int bodyTextColor = swatch.getBodyTextColor();
                int rgbColor = swatch.getRgb();

                //set colors to player views
                //status and navigation bar color
                getWindow().setStatusBarColor(rgbColor);
                getWindow().setNavigationBarColor(rgbColor);

                //more view colors
                songNameView.setTextColor(titleTextColor);
                playerCloseBtn.getCompoundDrawables()[0].setTint(titleTextColor);
                progressView.setTextColor(bodyTextColor);
                durationView.setTextColor(bodyTextColor);

                repeatModeBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                skipPrevBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                skipNextBtn.getCompoundDrawables()[0].setTint(bodyTextColor);
                playPauseBtn.getCompoundDrawables()[0].setTint(titleTextColor);
                playlistBtn.getCompoundDrawables()[0].setTint(bodyTextColor);

            }
        });
    }

    private void exitPlayerView() {
        playerView.setVisibility(View.GONE);
        getWindow().setStatusBarColor(defaultStatusColor);
        getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(defaultStatusColor, 199)); // 0 & 255
    }

    private void userResponesesOnRecordAudioPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if (shouldShowRequestPermissionRationale(recordAudioPermission)){
                //Show an educational UI to user explaining why we need this permission
                //use alert dialog
                new  AlertDialog.Builder(this)
                        .setTitle("Requesting to show Audio Visualizer")
                        .setMessage("Allow this app to display  audio visualizer when music is playing")
                        .setPositiveButton("Allow", (dialogInterface, i) -> {
                            //request the perm
                            recordAudioPermissionLaucher.launch(recordAudioPermission);
                        })
                        .setNegativeButton("No", (dialogInterface, i) -> {
                            Toast.makeText(getApplicationContext(), "You denied to show the audio visualizer", Toast.LENGTH_SHORT).show();
                            dialogInterface.dismiss();
                        })
                        .show();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "You denied to show the audio visualizer", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //audio visualizer
    private void activateAudioVisualizer() {
        //check if we have record audio permission to show an audio visualizer
        if (ContextCompat.checkSelfPermission(this, recordAudioPermission) != PackageManager.PERMISSION_GRANTED){
            return;
        }

        //set color to the audio visualizer
        audioVisualizer.setColor(ContextCompat.getColor(this, R.color.secondary_color));
        //set number of visualizer btn 10 &256
        audioVisualizer.setDensity(50);
        //set the audio session id from the player
        audioVisualizer.setPlayer(player.getAudioSessionId());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //release the player
//        if (player.isPlaying()){
//            player.stop();
//        }
//        player.release();
        doUnbindService();
        
    }

    private void doUnbindService() {
        if (isBound){
            unbindService(playerServiceConnection);
            isBound = false;
        }
    }

    private void userRespones() {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED){
            //Fetch songs
            fetchSongs();
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ){
            if (shouldShowRequestPermissionRationale(permission)){
                //Show an educational UI to user explaining why we need this permission
                //use alert dialog
                new  AlertDialog.Builder(this)
                        .setTitle("Requesting Permission")
                        .setMessage("Allow us to fetch song from your device")
                        .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                               //Request permission
                               storagePermissionLauncher.launch(permission);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Toast.makeText(getApplicationContext(), "You denied us to show songs", Toast.LENGTH_SHORT).show();
                                dialogInterface.dismiss();
                            }
                        })
                        .show();
            }
        }
        else
        {
            Toast.makeText(this, "You canceled to show songs", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchSongs() {
        //Define a list to carry songs
        List<Song> songs = new ArrayList<>();
        Uri mediaStoreUri;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            mediaStoreUri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        }
        else{
            mediaStoreUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        //Define projection
        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
        };

        //Order
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED ;

        //Get the songs
        try (Cursor cursor = getContentResolver().query(mediaStoreUri, projection, null, null, sortOrder)){
            // Catch cursor indices
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);

            //clear the previous loaded before adding loading again
            while (cursor.moveToNext()){
                //get the values of a column for a given audio file
                long id = cursor.getLong(idColumn);
                String name = cursor.getString(nameColumn);
                int duration = cursor.getInt(durationColumn);
                int size = cursor.getInt(sizeColumn);
                long albumId = cursor.getLong(albumIdColumn);

                // Song uri
                Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);

                // Album artwork uri
                Uri albumArtworkUri = ContentUris.withAppendedId(Uri.parse("context://media/external/audio/albumart"), albumId);

                // Remove extension part from the song's name
                name = name.toString().replace(".mp3", "").replace(".wav", "").replace(".amr", "");

                //Song items
                Song song = new Song(name, uri, albumArtworkUri, size, duration);

                // Add song items to song list
                songs.add(song);

            }

            // Display songs
            showSongs(songs);

        }
//        catch (Exception e){
//            e.printStackTrace();
//        }
    }

    private void showSongs(List<Song> songs) {

        if (songs.size() == 0){
            Toast.makeText(this, "No Songs", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save songs
        allSongs.clear();
        allSongs.addAll(songs);

        // Update the tool bar title
        String title = getResources().getString(R.string.app_name) + " - " + songs.size() + " songs";
        Objects.requireNonNull(getSupportActionBar()).setTitle(title);

        // Layout Manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // Songs Adapter
        songAdapter = new SongAdapter(this, songs, player, playerView);

        //recyclerview animators optional
        ScaleInAnimationAdapter scaleInAnimationAdapter = new ScaleInAnimationAdapter(songAdapter);
        scaleInAnimationAdapter.setDuration(1000);
        scaleInAnimationAdapter.setInterpolator(new OvershootInterpolator());
        scaleInAnimationAdapter.setFirstOnly(false);
        recyclerView.setAdapter(scaleInAnimationAdapter);

    }

    //setting the menu/search btn
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search_btn, menu);

        //search btn item
        MenuItem menuItem = menu.findItem(R.id.seachBtn);
        SearchView searchView = (SearchView) menuItem.getActionView();

        //search song method
        SearchSong(searchView);
        return super.onCreateOptionsMenu(menu);
    }

    private void SearchSong(SearchView searchView) {
        //search view listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                //filter songs
                filterSongs(newText.toLowerCase());
                return true;
            }
        });
    }

    private void filterSongs(String query) {
        List<Song> filteredList = new ArrayList<>();

        if (allSongs.size() > 0){
            for (Song song : allSongs){
                if (song.getTitle().toLowerCase().contains(query)){
                    filteredList.add(song);
                }
            }

            if (songAdapter != null){
                songAdapter.filterSong(filteredList);
            }
        }
    }
}
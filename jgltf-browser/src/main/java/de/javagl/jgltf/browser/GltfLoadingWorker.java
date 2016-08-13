/*
 * www.javagl.de - JglTF
 *
 * Copyright 2015-2016 Marco Hutter - http://www.javagl.de
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package de.javagl.jgltf.browser;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.text.NumberFormat;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import de.javagl.jgltf.browser.io.GltfDataReaderThreaded;
import de.javagl.jgltf.model.GltfData;
import de.javagl.jgltf.model.io.IO;
import de.javagl.swing.tasks.ProgressListener;
import de.javagl.swing.tasks.SwingTask;
import de.javagl.swing.tasks.SwingTaskExecutors;
import de.javagl.swing.tasks.SwingTaskViews;

/**
 * The worker class for loading glTF data from a URI. This is a swing task
 * that performs the loading in a background thread, while showing a modal
 * dialog with progress information. 
 */
final class GltfLoadingWorker extends SwingTask<GltfData, Object>
{
    /**
     * The logger used in this class
     */
    private static final Logger logger = 
        Logger.getLogger(GltfBrowserApplication.class.getName());
    
    /**
     * The application which created this worker
     */
    private final GltfBrowserApplication owner;
    
    /**
     * The main frame of the application, used as parent for dialogs
     */
    private final JFrame frame;
    
    /**
     * The URI that the data is loaded from
     */
    private final URI uri;

    /**
     * The reader that will execute the background tasks
     */
    private final GltfDataReaderThreaded gltfDataReaderThreaded;

    /**
     * The {@link GltfLoaderPanel} that will be shown in the modal
     * dialog while this worker is running, and which will display
     * the progress information and possible error messages.
     */
    private final GltfLoaderPanel gltfLoaderPanel;

    /**
     * Creates a new worker
     * 
     * @param owner The application which created this worker
     * @param frame The parent frame for dialogs
     * @param uri The URI to load the data from
     */
    GltfLoadingWorker(GltfBrowserApplication owner, JFrame frame, URI uri)
    {
        this.owner = owner;
        this.frame = frame;
        this.uri = uri;
        
        this.gltfDataReaderThreaded = new GltfDataReaderThreaded(-1);
        this.gltfLoaderPanel = new GltfLoaderPanel(
            gltfDataReaderThreaded.getObservableExecutorService());
    }
    
    /**
     * Load the data from the URI that was given in the constructor,
     * showing a modal dialog with progress information.
     */
    void load()
    {
        SwingTaskExecutors.create(this)
            .setTitle("Loading")
            .setMillisToPopup(0)
            .setSwingTaskViewFactory(c -> 
                SwingTaskViews.create(c, gltfLoaderPanel, false))
            .setCancelable(true)
            .build()
            .execute();
    }
        
    @Override
    protected GltfData doInBackground() throws IOException
    {
        String message = "Loading glTF from " + IO.extractFileName(uri);
        long contentLength = IO.getContentLength(uri);
        if (contentLength >= 0)
        {
            String contentLengthString = 
                NumberFormat.getNumberInstance().format(contentLength);
            message += " (" + contentLengthString + " bytes)";
        }
        setMessage(message);
        
        ProgressListener progressListener = new ProgressListener()
        {
            @Override
            public void messageChanged(String message)
            {
                setMessage(message);
            }
            @Override
            public void progressChanged(double progress)
            {
                setProgress(progress);
            }
        }; 
        gltfDataReaderThreaded.addProgressListener(progressListener);
        gltfDataReaderThreaded.setJsonErrorConsumer(jsonError ->
        {
            String jsonErrorString = 
                jsonError.getMessage() + ",\n at JSON path: " + 
                jsonError.getJsonPathString() + "\n";
            gltfLoaderPanel.appendMessage(jsonErrorString);
        });
        return gltfDataReaderThreaded.readGltfData(uri);
    }

    @Override
    protected void done()
    {
        try
        {
            GltfData gltfData = get();
            String fileName = IO.extractFileName(uri);
            owner.createGltfBrowserPanel(fileName, gltfData);
        } 
        catch (CancellationException e)
        {
            logger.info("Cancelled loading " + uri + 
                " (" + e.getMessage() + ")");
            gltfDataReaderThreaded.cancel();
            //e.printStackTrace();
            return;
        }
        catch (InterruptedException e)
        {
            logger.info("Interrupted while loading " + uri + 
                " (" + e.getMessage() + ")");
            gltfDataReaderThreaded.cancel();
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e)
        {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            StringBuilder sb = new StringBuilder();
            sb.append("Loading error: " + e.getMessage() + 
                "\n" + sw.toString());
            JOptionPane.showMessageDialog(frame,
                sb.toString(), "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
    }
            
}
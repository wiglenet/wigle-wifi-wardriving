package net.wigle.m8b;

/**
 * Interface to let us send progress from inside the m8b generate process
 */
public interface M8bProgress {

    // progress message for mjg
    void handleGenerationProgress(int linesProcessed);

    // progress message for output write
    void handleWriteProgress(int elements, int elementsWritten);
}

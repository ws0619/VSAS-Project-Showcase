package au.edu.sydney.a2t0303.storage;

import au.edu.sydney.a2t0303.model.Scroll;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface ScrollRepository {

    // Retrieve all scrolls
    List<Scroll> findAll() throws IOException;

    // Save a new scroll
    void save(Scroll scroll) throws IOException;

    // Find a scroll by ID
    default Optional<Scroll> findById(String id)
            throws IOException {

        return findAll().stream()
                .filter(scroll -> scroll.id().equals(id))
                .findFirst();
    }

    // Update an existing scroll
    default void update(Scroll scroll) throws IOException {
        throw new UnsupportedOperationException(
                "Updating scrolls is not supported."
        );
    }

    // Delete a scroll by ID
    default void deleteById(String id) throws IOException {
        throw new UnsupportedOperationException(
                "Deleting scrolls is not supported."
        );
    }

    // Increment the download count after a successful download
    default void incrementDownloadCount(String scrollId)
            throws IOException {

        throw new UnsupportedOperationException(
                "Download counting is not supported."
        );
    }

}

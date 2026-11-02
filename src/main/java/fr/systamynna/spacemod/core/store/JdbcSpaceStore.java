package fr.systamynna.spacemod.core.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import fr.systamynna.spacemod.core.SpaceObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/** Store JDBC, SQL portable (SQLite, MySQL, PostgreSQL...). */
public class JdbcSpaceStore implements SpaceStore {
    private final Connection con;

    public JdbcSpaceStore(Connection con) {
        this.con = con;
        try (Statement st = con.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS space_object (uuid VARCHAR(36) PRIMARY KEY, parent_uuid VARCHAR(36), data BLOB)");
            try {
                st.execute("CREATE INDEX idx_space_object_parent ON space_object (parent_uuid)");
            } catch (SQLException exists) {
                // shortcut: pas de IF NOT EXISTS (MySQL ne l'a pas) ; l'échec vient presque toujours d'un index déjà présent
            }
        } catch (SQLException e) {
            throw io(e);
        }
    }

    @Override
    public void save(Collection<SpaceObject> objects) {
        try (PreparedStatement up = con.prepareStatement("UPDATE space_object SET parent_uuid = ?, data = ? WHERE uuid = ?");
             PreparedStatement in = con.prepareStatement("INSERT INTO space_object (parent_uuid, data, uuid) VALUES (?, ?, ?)")) {
            boolean auto = con.getAutoCommit();
            con.setAutoCommit(false);
            try {
                for (SpaceObject o : objects) {
                    byte[] data = toBytes(o.save());
                    String parent = o.getParent() == null ? null : o.getParent().toString();
                    up.setString(1, parent);
                    up.setBytes(2, data);
                    up.setString(3, o.getUuid().toString());
                    if (up.executeUpdate() == 0) {
                        in.setString(1, parent);
                        in.setBytes(2, data);
                        in.setString(3, o.getUuid().toString());
                        in.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(auto);
            }
        } catch (SQLException e) {
            throw io(e);
        }
    }

    @Override
    public Optional<SpaceObject> load(UUID uuid) {
        try (PreparedStatement ps = con.prepareStatement("SELECT uuid, parent_uuid, data FROM space_object WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw io(e);
        }
    }

    @Override
    public Optional<SpaceObject> loadRoot() {
        try (PreparedStatement ps = con.prepareStatement("SELECT uuid, parent_uuid, data FROM space_object WHERE parent_uuid IS NULL LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(read(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw io(e);
        }
    }

    @Override
    public List<SpaceObject> loadChildren(UUID parent) {
        try (PreparedStatement ps = con.prepareStatement("SELECT uuid, parent_uuid, data FROM space_object WHERE parent_uuid = ?")) {
            ps.setString(1, parent.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<SpaceObject> out = new ArrayList<>();
                while (rs.next()) out.add(read(rs));
                return out;
            }
        } catch (SQLException e) {
            throw io(e);
        }
    }

    @Override
    public void delete(UUID uuid) {
        try (PreparedStatement ps = con.prepareStatement("DELETE FROM space_object WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw io(e);
        }
    }

    @Override
    public void close() {
        try {
            con.close();
        } catch (SQLException e) {
            throw io(e);
        }
    }

    private static SpaceObject read(ResultSet rs) throws SQLException {
        String parent = rs.getString(2);
        return SpaceObject.load(UUID.fromString(rs.getString(1)), parent == null ? null : UUID.fromString(parent),
                fromBytes(rs.getBytes(3)));
    }

    private static byte[] toBytes(CompoundTag tag) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CompoundTag fromBytes(byte[] b) {
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(b), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static UncheckedIOException io(SQLException e) {
        return new UncheckedIOException(new IOException(e));
    }
}

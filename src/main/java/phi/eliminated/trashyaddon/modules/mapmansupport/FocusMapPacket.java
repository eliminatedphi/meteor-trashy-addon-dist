package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public record FocusMapPacket(int id) implements MapmanServerboundPacket {
    @Override
    public ByteBuffer encode() {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        b.putInt(id);
        b.flip();
        return b;
    }
}

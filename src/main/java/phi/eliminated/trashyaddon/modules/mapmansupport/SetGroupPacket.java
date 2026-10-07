package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public record SetGroupPacket(int w, int h, List<Integer> maps) implements MapmanServerboundPacket {
    @Override
    public ByteBuffer encode() {
        ByteBuffer b = ByteBuffer.allocate(4 * (maps.size() + 2)).order(ByteOrder.nativeOrder());
        b.putInt(w);
        b.putInt(h);
        for (int m : maps) {
            b.putInt(m);
        }
        b.flip();
        return b;
    }
}

// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;

public interface MapmanClientboundPacket {
    byte HIGHLIGHT_MAP = 0;
    byte REQUEST_MAP_DATA = 1;

    void decode(ByteBuffer data);
}

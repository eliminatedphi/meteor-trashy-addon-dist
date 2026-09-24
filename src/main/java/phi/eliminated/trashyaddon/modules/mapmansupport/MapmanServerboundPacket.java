// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon.modules.mapmansupport;

import java.nio.ByteBuffer;

public interface MapmanServerboundPacket {
    byte CLIENT_CONNECT = 0;
    byte MAP_FOUND_FRAME = 1;
    byte MAP_FOUND_CONTAINER = 2;
    byte FOCUS_MAP = 3;

    ByteBuffer encode();
}

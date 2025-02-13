package chip;

import java.io.*;
import java.util.Random;

public class Chip {
    //memory represented by a char array
    //each char starts with 0x00, then its byte of data
    private char[] memory;
    //registers
    private char[] V;
    //address pointer
    //12 out of 16 bits will be used
    private char I;
    // program counter
    private char pc;

    //stack and stack pointer
    private char stack[];
    private char stackPointer;

    // tick at 60hz
    private int delay_timer;
    private int sound_timer;

    //keys that can be inputted
    private byte[] keys;

    // 0 = black, 1 = white
    private byte[] display;

    // do we need to redraw the screen
    private boolean needRedraw;

    public void init() {
        //4 kB of memory
        memory = new char[4096];
        //16 registers
        V = new char[16];
        I = 0x0;
        //program starts at 0x200(512)
        pc = 0x200;

        stack = new char[16];
        stackPointer = 0;

        delay_timer = 0;
        sound_timer = 0;

        // 16 different keys
        keys = new byte[16];
        // 64 x 32
        display = new byte[64 * 32];
        needRedraw = false;
        loadFontset();
    }

    public void run() {
        //fetch operation code ("Opcode")
        //left shift to get upper byte, then combine with an or
        char opcode = (char) ((memory[pc] << 8) | memory[pc + 1]);
        System.out.println(Integer.toHexString(opcode));
        //decode Opcode
        // a bunch of cases for each possible opcode
        //first 4 bits indicate type of operation, rest are arguements
        switch(opcode & 0xF000) {

            case 0x0000: {//multiple cases
                switch(opcode & 0x00FF) {
                    case 0x00E0: {//clear screen
                        for(int i = 0; i < display.length; i++) {
                            display[i] = 0;
                        }
                        pc += 2;
                        needRedraw = true;
                        break;
                    }

                    case 0x00EE: {//return from subroutine by getting return address
                        stackPointer--;
                        pc = (char)(stack[stackPointer] + 2);
                        break;
                    }

                    default: {//0NNN: Calls RCA 1802 Program at address NNN
                        System.err.println("00__: Unsupported Opcode!");
                        System.exit(0);
                        break;
                    }
                }
                break;
            }

            case 0x1000: {//1NNN: Jumps to address NNN
                int NNN = opcode & 0x0FFF;
                pc = (char)NNN;
                break;
            }

            case 0x2000: {//2NNN: Calls subroutine at NNN
                //extract NNN
                char address = (char) (opcode & 0x0FFF);
                //push program code into stack
                stack[stackPointer] = pc;
                stackPointer++;
                //move to new address
                pc = address;
                break;
            }

            case 0x3000: {//3XNN: Skips the next instruction if V[X] equals NN
                int x = (opcode & 0x0F00) >> 8;
                int nn = (opcode & 0x00FF);
                // jump ahead 4 bytes if V[X] == NN, 2 otherwise
                if (V[x] == nn) {
                    pc += 4;
                } else {
                    pc += 2;
                }
                break;
            }

            case 0x4000: {//3XNN: Skips the next instruction if V[X] does not equal NN
                int x = (opcode & 0x0F00) >> 8;
                int nn = (opcode & 0x00FF);
                // jump ahead 4 bytes if V[X] != NN, 2 otherwise
                if (V[x] != nn) {
                    pc += 4;
                } else {
                    pc += 2;
                }
                break;
            }

            case 0x5000: {//5XY0: Skips the next instruction if V[X] equals V[Y]
                int x = (opcode & 0x0F00) >> 8;
                int y = (opcode & 0x00F0) >> 4;
                // jump ahead 4 bytes if V[X] == V[Y], 2 otherwise
                if (V[x] != V[y]) {
                    pc += 4;
                } else {
                    pc += 2;
                }
                break;
            }

            case 0x6000: {//6XNN: Set VX to NN
                int x = (opcode & 0x0F00) >> 8;
                V[x] = (char) (opcode & 0x00FF);
                //advance to next operation
                pc += 2;
                break;
            }

            case 0x7000: {//7XNN: Adds NN to VX
                int x = (opcode & 0x0F00) >> 8;
                int nn = (opcode & 0x00FF);
                // and 0xFF for overflow
                V[x] = (char) ((V[x] + nn) & 0xFF);
                pc += 2;
                break;
            }

            case 0x8000: {//multicase
                switch (opcode & 0x000F) {

                    case 0x0000: { //8XY0: Sets VX to the value of VY
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.println("Setting V[" + x + "] to " + (int)V[y]);
                        V[x] = V[y];
                        pc += 2;
                        break;
                    }

                    case 0x0001: { //8XY1 Sets VX to VX or VY.
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.println("Setting V[" + x + "] = V[" + x + "] | V[" + y + "]");
                        V[x] = (char)((V[x] | V[y]) & 0xFF);
                        pc += 2;
                        break;
                    }

                    case 0x0002: { //8XY2: Sets VX to VX & VY
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.println("Set V[" + x + "] to V[" + x + "] = " + (int)V[x] + " & V[" + y + "] = " + (int)V[y] + " = " + (int)(V[x] & V[y]));
                        V[x] = (char)(V[x] & V[y]);
                        pc += 2;
                        break;
                    }

                    case 0x0004: { //Adds VY to VX. VF is set to 1 when carry applies else to 0
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.print("Adding V[" + x + "] (" + (int)V[x]  + ") to V[" + y + "] (" + (int)V[y]  + ") = " + ((V[x] + V[y]) & 0xFF) + ", ");
                        if(V[y] > 0xFF - V[x]) {
                            V[0xF] = 1;
                            System.out.println("Carry!");
                        } else {
                            V[0xF] = 0;
                            System.out.println("No Carry");
                        }
                        V[x] = (char)((V[x] + V[y]) & 0xFF);
                        pc += 2;
                        break;
                    }

                    case 0x0005: { //VY is subtracted from VX. VF is set to 0 when there is a borrow else 1
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;
                        System.out.print("V[" + x + "] = " + (int)V[x] + " V[" + y + "] = " + (int)V[y] + ", ");
                        if(V[x] > V[y]) {
                            V[0xF] = 1;
                            System.out.println("No Borrow");
                        } else {
                            V[0xF] = 0;
                            System.out.println("Borrow");
                        }
                        V[x] = (char)((V[x] - V[y]) & 0xFF);
                        pc += 2;
                        break;
                    }

                    case 0x0006: { //8XY6: Shift VX right by one, VF is set to the least significant bit of VX
                        int x = (opcode & 0x0F00) >> 8;
                        V[0xF] = (char)(V[x] & 0x1);
                        V[x] = (char)(V[x] >> 1);
                        pc += 2;
                        System.out.println("Shift V[ " + x + "] >> 1 and VF to LSB of VX");
                        break;
                    }

                    case 0x0007: { //8XY7 Sets VX to VY minus VX. VF is set to 0 when there's a borrow, and 1 when there isn't.
                        int x = (opcode & 0x0F00) >> 8;
                        int y = (opcode & 0x00F0) >> 4;

                        if(V[x] > V[y])
                            V[0xF] = 0;
                        else
                            V[0xF] = 1;

                        V[x] = (char)((V[y] - V[x]) & 0xFF);
                        System.out.println("V[" + x + "] = V[" + y + "] - V[" + x + "], Applies Borrow if needed");

                        pc += 2;
                        break;
                    }

                    case 0x000E: { //8XYE Shifts VX left by one. VF is set to the value of the most significant bit of VX before the shift.
                        int x = (opcode & 0x0F00) >> 8;
                        V[0xF] = (char)(V[x] & 0x80);
                        V[x] = (char)(V[x] << 1);
                        pc += 2;
                        System.out.println("Shift V[ " + x + "] << 1 and VF to MSB of VX");
                        break;
                    }

                    default:
                        System.err.println("Unsupported Opcode!");
                        System.exit(0);
                        break;
                }
                break;
            }

            case 0x9000: { //9XY0 Skips the next instruction if VX doesn't equal VY.
                int x = (opcode & 0x0F00) >> 8;
                int y = (opcode & 0x00F0) >> 4;
                if(V[x] != V[y]) {
                    System.out.println("Skipping next instruction V[" + x + "] != V[" + y + "]");
                    pc += 4;
                } else {
                    System.out.println("Skipping next instruction V[" + x + "] !/= V[" + y + "]");
                    pc += 2;
                }
                break;
            }

            case 0xA000: {//ANNN: Set I to NNN
                I = (char) (opcode & 0x0FFF);
                pc += 2;
                break;
            }

            case 0xB000: { //BNNN Jumps to the address NNN plus V0.
                int nnn = opcode & 0x0FFF;
                int extra = V[0] & 0xFF;
                pc = (char)(nnn + extra);
                break;
            }

            case 0xC000: { //CXNN: Set V[X] to random number & NN
                int x = (opcode & 0x0F00) >> 8;
                int nn = (opcode & 0x00FF);
                int randomNumber = new Random().nextInt(256) & nn;
                System.out.println("V[" + x + "] has been set to (randomised) " + randomNumber);
                V[x] = (char)randomNumber;
                pc += 2;
                break;
            }

            case 0xD000: {//DXYN: Draw a sprite at coordinate (V[X], V[Y]), with height 8, and width N. Sprite is located at I
                //draw by XOR'ing display array
                //Check Collision Flag register V[0xF] and update
                int x = V[(opcode & 0x0F00) >> 8];
                int y = V[(opcode & 0x00F0) >> 4];
                int height = opcode & 0x000F;

                V[0xF] = 0;

                for(int _y = 0; _y < height; _y++) {
                    //move to different line in memory for each pixel of height
                    int line = memory[I + _y];
                    for(int _x = 0; _x < 8; _x++) {
                        //grab pixel located in x'th position
                        int pixel = line & (0x80 >> _x);
                        // if there are any pixels to set
                        if(pixel != 0) {
                            // calculate pixel coordinate for display array
                            int totalX = (x + _x) % 64;
                            int totalY = (y + _y) % 32;
                            int index = (totalY * 64) + totalX;

                            //if pixel at location is already 1, set collision flag to 1
                            if(display[index] == 1) {
                                V[0xF] = 1;
                            }
                            //use XOR to set pixel
                            display[index] ^= 1;
                        }
                    }
                }
                pc += 2;
                needRedraw = true;
                break;
            }

            case 0xE000: {//multicase
                switch (opcode & 0x00FF) {
                    case 0x009E: { //EX9E Skip the next instruction if the Key VX is pressed
                        int x = (opcode & 0x0F00) >> 8;
                        int key = V[x];
                        if(keys[key] == 1) {
                            pc += 4;
                        } else {
                            pc += 2;
                        }
                        break;
                    }

                    case 0x00A1: { //EXA1 Skip the next instruction if the Key VX is NOT pressed
                        int x = (opcode & 0x0F00) >> 8;
                        int key = V[x];
                        if(keys[key] == 0) {
                            pc += 4;
                        } else {
                            pc += 2;
                        }
                        break;
                    }

                    default:
                        System.err.println("Unexisting opcode");
                        System.exit(0);
                        return;
                }
                break;
            }

            case 0xF000: {//multicase
                switch(opcode & 0x00FF) {

                    case 0x0007: { //FX07: Set VX to the value of delay_timer
                        int x = (opcode & 0x0F00) >> 8;
                        V[x] = (char)delay_timer;
                        pc += 2;
                    }

                    case 0x000A: { //FX0A A key press is awaited, and then stored in VX.
                        int x = (opcode & 0x0F00) >> 8;
                        for(int i = 0; i < keys.length; i++) {
                            if(keys[i] == 1) {
                                V[x] = (char)i;
                                pc += 2;
                                break;
                            }
                        }
                        System.out.println("Awaiting key press to be stored in V[" + x + "]");
                        break;
                    }

                    case 0x0015: { //FX15: Set delay timer to V[x]
                        int x = (opcode & 0x0F00) >> 8;
                        delay_timer = V[x];
                        pc += 2;
                    }

                    case 0x0018: { //FX18: Set the sound timer to V[x]
                        int x = (opcode & 0x0F00) >> 8;
                        sound_timer = V[x];
                        pc += 2;
                        break;
                    }

                    case 0x001E: { //FX1E: Adds VX to I
                        int x = (opcode & 0x0F00) >> 8;
                        I = (char)(I + V[x]);
                        System.out.println("Adding V[" + x + "] = " + (int)V[x] + " to I");
                        pc += 2;
                        break;
                    }

                    case 0x0029: {//Sets I to the location of the sprite for the character VX (Fontset)
                        int x = (opcode & 0x0F00) >> 8;
                        int character = V[x];
                        // each character takes up 5 bytes
                        I = (char)(0x050 + (character * 5));
                        pc += 2;
                        break;
                    }

                    case 0x0033: {//FX33 Store the binary-coded decimal value at V[X] with hundreds place at I, tens place at I + 1, and  ones place at I + 2
                        int x = (opcode & 0x0F00) >> 8;
                        int value = V[x];
                        int hundreds = (value - (value % 100)) / 100;
                        value -= hundreds * 100;
                        int tens = (value - (value % 10))/ 10;
                        value -= tens * 10;
                        memory[I] = (char)hundreds;
                        memory[I + 1] = (char)tens;
                        memory[I + 2] = (char)value;
                        pc += 2;
                        break;
                    }

                    case 0x0055: { //FX55 Stores V0 to VX in memory starting at address I.
                        int x = (opcode & 0x0F00) >> 8;
                        for(int i = 0; i <= x; i++) {
                            memory[I + i] = V[i];
                        }
                        System.out.println("Setting Memory[" + Integer.toHexString(I & 0xFFFF).toUpperCase() + " + n] = V[0] to V[x]");
                        pc += 2;
                        break;
                    }

                    case 0x0065: {//FX65 Fills V0 to VX with values starting from I
                        int x = (opcode & 0x0F00) >> 8;
                        for(int i = 0; i <= x; i++) {
                            V[i] = memory[I + i];
                        }
                        // increment memory pointer
                        I = (char)(I + x + 1);
                        pc += 2;
                        break;
                    }

                    default: {
                        System.err.println("F___: Unsupported Opcode!");
                        System.exit(0);
                    }
                }
                break;
            }
            default: {
                System.err.println("Unsupported Opcode!");
                System.exit(0);
            }
        }
        if (sound_timer > 0) {
            sound_timer--;
        }
        if (delay_timer > 0) {
            delay_timer--;
        }
    }

    public byte[] getDisplay() {
        return display;
    }

    public boolean needsRedraw() {
        return needRedraw;
    }

    public void removeDrawFlag() {
        needRedraw = false;
    }

    public void loadProgram(String file) {
        DataInputStream input = null;
        try {
            input = new DataInputStream(new FileInputStream(file));
            int offset = 0;
            while (input.available() > 0) {
                // program starts at 0x200 & mask last Byte
                memory[0x200 + offset] = (char)(input.readByte() & 0xFF);
                offset++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(0);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {}
            }
        }
    }

    //loads fontset into memory
    public void loadFontset() {
        for(int i = 0; i < ChipData.fontset.length; i++) {
            memory[0x50 + i] = (char)(ChipData.fontset[i] & 0xFF);
        }
    }

    public void setKeyBuffer(int[] keyBuffer) {
        for(int i = 0; i < keys.length; i++) {
            keys[i] = (byte)keyBuffer[i];
        }
    }
}

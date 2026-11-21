import { rpc } from "./ui.js";
import { state } from "./state.js";

export function isLocal() {
  return state.session?.source === "ssh";
}

export function localRoot() {
  return state.session?.remote?.root || "";
}

export function isLocalSession(session) {
  return session?.source === "ssh";
}

export const ws = {
  isLocal,

  async list(dir = "") {
    if (isLocal()) return rpc(window.clauden.local.list(dir));
    return rpc(window.clauden.code.files(state.session.id, dir));
  },

  async read(file) {
    if (isLocal()) return rpc(window.clauden.local.read(file));
    return rpc(window.clauden.code.readFile(state.session.id, file));
  },

  async write(file, content) {
    if (isLocal()) return rpc(window.clauden.local.write(file, content));
    return rpc(window.clauden.code.writeFile(state.session.id, file, content));
  },

  async create(dir, name, kind = "file") {
    if (isLocal()) return rpc(window.clauden.local.create(dir, name, kind));
    return rpc(window.clauden.code.createFile(state.session.id, dir, name, kind));
  },

  async rename(file, name) {
    if (isLocal()) return rpc(window.clauden.local.rename(file, name));
    return rpc(window.clauden.code.renameFile(state.session.id, file, name));
  },

  async remove(file) {
    if (isLocal()) return rpc(window.clauden.local.remove(file));
    return rpc(window.clauden.code.deleteFile(state.session.id, file));
  },

  async diff() {
    if (isLocal()) return rpc(window.clauden.local.diff());
    return rpc(window.clauden.code.diff(state.session.id));
  },

  async commit(message) {
    if (isLocal()) return rpc(window.clauden.local.commit(message));
    return rpc(window.clauden.code.commit(state.session.id, message));
  },
};

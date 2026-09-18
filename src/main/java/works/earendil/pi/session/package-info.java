/**
 * 历史会话文件的本地只读访问。
 *
 * <p>PI 的 JSONL RPC 协议不提供会话枚举命令，本包在文件系统层面读取会话文件，
 * 用于构建会话列表页、恢复入口或离线查看，不依赖 PI 子进程。</p>
 */
package works.earendil.pi.session;

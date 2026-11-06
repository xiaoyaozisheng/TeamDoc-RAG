import { useState } from 'react';
import { Form, Input, message, Modal } from 'antd';
import { authService } from '../services/auth';

interface Props {
  open: boolean;
  onClose: () => void;
}

interface FormValues {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** 自助修改密码（P1）：成功后所有设备强制重新登录 */
export const ChangePasswordModal = ({ open, onClose }: Props): React.ReactNode => {
  const [form] = Form.useForm<FormValues>();
  const [submitting, setSubmitting] = useState(false);

  const handleOk = async (): Promise<void> => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await authService.changePassword(values.oldPassword, values.newPassword);
      message.success('密码修改成功，请重新登录');
      onClose();
      // authService.changePassword 内部已清空本地令牌并跳转登录页
    } catch (err) {
      const apiMessage = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      if (apiMessage) {
        message.error(apiMessage);
      }
      // 表单校验错误由 antd 自动展示，静默即可
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      title="修改密码"
      open={open}
      onOk={() => void handleOk()}
      onCancel={() => {
        form.resetFields();
        onClose();
      }}
      confirmLoading={submitting}
      okText="确认修改"
      cancelText="取消"
      destroyOnClose
    >
      <p className="mb-4 text-sm text-gray-500">修改成功后将撤销所有已登录设备的会话，需重新登录。</p>
      <Form form={form} layout="vertical" autoComplete="off">
        <Form.Item
          name="oldPassword"
          label="原密码"
          rules={[{ required: true, message: '请输入原密码' }]}
        >
          <Input.Password placeholder="输入当前密码" />
        </Form.Item>
        <Form.Item
          name="newPassword"
          label="新密码"
          rules={[
            { required: true, message: '请输入新密码' },
            { min: 6, max: 64, message: '密码长度必须在 6-64 位之间' },
          ]}
        >
          <Input.Password placeholder="至少 6 位" />
        </Form.Item>
        <Form.Item
          name="confirmPassword"
          label="确认新密码"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve();
                }
                return Promise.reject(new Error('两次输入的密码不一致'));
              },
            }),
          ]}
        >
          <Input.Password placeholder="再次输入新密码" />
        </Form.Item>
      </Form>
    </Modal>
  );
};
